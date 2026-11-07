package com.doubleclick.wadii;

import com.doubleclick.wadii.support.IntegrationTest;
import com.doubleclick.wadii.support.TestApi.Actor;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.doubleclick.wadii.support.TestApi.money;
import static org.assertj.core.api.Assertions.assertThat;

/** Cancellation, no-show, interruption and disputes (business rules sections 8 and 13). */
class OrderLifecycleSpecTest extends IntegrationTest {

    @Test
    void workInProgressCannotBeCanceledAndUsesTheInterruptionFlow() {
        Actor customer = api.customer();
        Actor p = api.provider("REPAIRS");
        long id = order(customer, p, "REPAIRS", null).path("id").asLong();
        api.post("/api/v1/orders/" + id + "/start", p, null).ok();

        assertThat(api.post("/api/v1/orders/" + id + "/cancel", customer, Map.of("reason", "changed mind")).code())
                .isEqualTo("ORDER_INVALID_STATE");
        api.post("/api/v1/orders/" + id + "/request-interruption", customer, Map.of("reason", "Need the car now")).ok();
        var tooMuch = api.post("/api/v1/orders/" + id + "/interruption-statement", p, Map.of("workDone", "Diagnosis",
                "laborCost", 100, "partsCost", 0, "proposedAmount", 99999));
        assertThat(tooMuch.code()).isEqualTo("VALIDATION_FAILED");
        String evidence = api.upload(p, "EVIDENCE");
        api.post("/api/v1/orders/" + id + "/interruption-statement", p, Map.of("workDone", "Diagnosis and parts removal",
                "laborCost", 100, "partsCost", 50, "proposedAmount", 150, "evidenceKeys", List.of(evidence))).ok();
        assertThat(api.rawStatus(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/files/" + evidence),
                customer)).as("customer can read the provider's evidence").isEqualTo(200);

        var done = api.post("/api/v1/orders/" + id + "/accept-interruption", customer, null).ok().data();
        assertThat(done.path("status").asText()).isEqualTo("INTERRUPTED");
        assertThat(money(done.path("amountDue"))).isEqualByComparingTo("150.00");
        assertThat(done.path("cashStatus").asText()).isEqualTo("DUE");
    }

    @Test
    void disputedInterruptionIsDecidedByStaffWithAFinalAmount() {
        Actor customer = api.customer();
        Actor p = api.provider("REPAIRS");
        long id = order(customer, p, "REPAIRS", null).path("id").asLong();
        api.post("/api/v1/orders/" + id + "/start", p, null).ok();
        api.post("/api/v1/orders/" + id + "/request-interruption", customer, Map.of("reason", "Stop")).ok();
        api.post("/api/v1/orders/" + id + "/interruption-statement", p, Map.of("workDone", "All of it",
                "laborCost", 400, "partsCost", 100, "proposedAmount", 500)).ok();
        long dispute = api.post("/api/v1/orders/" + id + "/dispute-interruption", customer,
                Map.of("description", "Nothing was done")).ok().data().path("id").asLong();

        Actor admin = api.admin();
        api.post("/api/v1/admin/disputes/" + dispute + "/assign", admin, null).ok();
        assertThat(api.get("/api/v1/admin/disputes/" + dispute, admin).ok().data().path("status").asText()).isEqualTo("UNDER_REVIEW");
        assertThat(api.get("/api/v1/admin/disputes/" + dispute, customer).status()).isEqualTo(403);
        var resolved = api.post("/api/v1/admin/disputes/" + dispute + "/resolve", admin,
                Map.of("outcome", "SET_FINAL_AMOUNT", "note", "Only diagnosis done", "finalAmount", 120)).ok().data();
        assertThat(resolved.path("status").asText()).isEqualTo("RESOLVED");
        var o = api.get("/api/v1/orders/" + id, customer).ok().data();
        assertThat(o.path("status").asText()).isEqualTo("INTERRUPTED");
        assertThat(money(o.path("amountDue"))).isEqualByComparingTo("120.00");
    }

    @Test
    void cashDisputeRaisesAReviewFlagWithoutBlockingAnyone() {
        Actor customer = api.customer();
        Actor p = api.provider("TIRES");
        long id = order(customer, p, "TIRES", null).path("id").asLong();
        api.post("/api/v1/orders/" + id + "/start", p, null).ok();
        api.post("/api/v1/orders/" + id + "/cash/report", p, Map.of("amount", 500)).ok();
        long dispute = api.post("/api/v1/orders/" + id + "/cash/dispute", customer, Map.of("description", "I never paid that"))
                .ok().data().path("id").asLong();
        var o = api.get("/api/v1/orders/" + id, customer).ok().data();
        assertThat(o.path("cashStatus").asText()).isEqualTo("DISPUTED");
        assertThat(o.path("status").asText()).as("execution untouched").isEqualTo("IN_PROGRESS");

        var flags = api.get("/api/v1/admin/fraud-flags?status=OPEN&size=100", api.admin()).ok().data().path("items");
        boolean flagged = false;
        for (JsonNode f : flags) flagged |= f.path("type").asText().equals("CASH_CONFLICT") && f.path("subjectUserId").asLong() == p.userId();
        assertThat(flagged).isTrue();
        api.get("/api/v1/provider/requests", p).ok();

        api.post("/api/v1/admin/disputes/" + dispute + "/resolve", api.admin(), Map.of("outcome", "CASH_NOT_COLLECTED", "note", "No proof")).ok();
        assertThat(api.get("/api/v1/orders/" + id, customer).ok().data().path("cashStatus").asText()).isEqualTo("DUE");
    }

    @Test
    void noShowCanBeReportedOnlyAfterTheGracePeriod() {
        Actor customer = api.customer();
        Actor p = api.provider("CAR_WASH");
        Instant start = Instant.now().plus(Duration.ofDays(1));
        long id = order(customer, p, "CAR_WASH", start).path("id").asLong();
        assertThat(api.post("/api/v1/orders/" + id + "/report-no-show", customer, null).code()).isEqualTo("NO_SHOW_TOO_EARLY");
        clock.advance(Duration.ofDays(1).plusMinutes(31));
        var o = api.post("/api/v1/orders/" + id + "/report-no-show", customer, null).ok().data();
        assertThat(o.path("status").asText()).isEqualTo("CANCELED");
        assertThat(o.path("cancellation").path("kind").asText()).isEqualTo("PROVIDER_NO_SHOW");
    }

    @Test
    void cancellationBeforeStartIsFreeAndReopensNothingAutomatically() {
        Actor customer = api.customer();
        Actor p = api.provider("BATTERIES");
        JsonNode order = order(customer, p, "BATTERIES", null);
        long id = order.path("id").asLong();
        var o = api.post("/api/v1/orders/" + id + "/cancel", p, Map.of("reason", "Out of stock")).ok().data();
        assertThat(o.path("cancellation").path("kind").asText()).isEqualTo("PROVIDER_CANCELED");
        assertThat(api.get("/api/v1/requests/" + order.path("requestId").asLong(), customer).ok().data().path("status").asText())
                .isEqualTo("CANCELED");
        assertThat(api.get("/api/v1/provider/ledger", p).ok().data().path("totalItems").asInt()).as("no fee in V1").isZero();
    }

    @Test
    void ordersAreInvisibleToUnrelatedUsers() {
        Actor customer = api.customer();
        Actor p = api.provider("ACCESSORIES");
        long id = order(customer, p, "ACCESSORIES", null).path("id").asLong();
        Actor stranger = api.customer();
        assertThat(api.get("/api/v1/orders/" + id, stranger).status()).isEqualTo(404);
        assertThat(api.post("/api/v1/orders/" + id + "/start", stranger, null).status()).isEqualTo(404);
        assertThat(api.post("/api/v1/orders/" + id + "/start", customer, null).status()).as("customer cannot act as provider").isEqualTo(403);
    }

    private JsonNode order(Actor customer, Actor provider, String category, Instant appointment) {
        JsonNode request = api.request(customer, category);
        long requestId = request.path("id").asLong();
        long item = api.itemId(request, category);
        long line = api.lineId(api.offer(provider, requestId, List.of(api.line(item, 400, false))), item);
        return appointment == null ? api.accept(customer, requestId, item, line) : api.accept(customer, requestId, item, line, appointment);
    }
}
