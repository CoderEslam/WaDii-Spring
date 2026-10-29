package com.doubleclick.wadii;

import com.doubleclick.wadii.support.IntegrationTest;
import com.doubleclick.wadii.support.MutableClock;
import com.doubleclick.wadii.support.TestApi;
import com.doubleclick.wadii.support.TestApi.Actor;
import com.doubleclick.wadii.support.TestApi.Result;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.doubleclick.wadii.support.TestApi.expectOk;
import static com.doubleclick.wadii.support.TestApi.expectStatus;
import static org.assertj.core.api.Assertions.assertThat;

class PolicyAndAftercareTest extends IntegrationTest {

    private static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");

    @Autowired
    TestApi api;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    MutableClock clock;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private Result publish(Actor p, String type, Map<String, Object> body) {
        return api.post("/api/v1/providers/me/policies/" + type + "/versions", p, body);
    }

    private Instant tomorrowAt(int hour) {
        return ZonedDateTime.now(CAIRO).plusDays(1).truncatedTo(ChronoUnit.DAYS).withHour(hour).toInstant();
    }

    private void payCash(Actor customer, Actor provider, long paymentId) {
        expectOk(api.post("/api/v1/payments/" + paymentId + "/method", customer, Map.of("method", "CASH")));
        expectOk(api.post("/api/v1/payments/" + paymentId + "/confirm", provider, null));
    }

    @Test
    void policiesAreVersionedAndOrdersKeepTheirSnapshot() {
        long s = api.newService();
        Actor p = api.verifiedProvider(List.of(s));
        expectStatus(201, publish(p, "RETURN", Map.of("terms", "7 day returns", "periodDays", 7)));
        Actor customer = api.customer();
        long orderId = api.acceptedOrder(customer, p, List.of(s), new BigDecimal("100"))[0];

        expectStatus(201, publish(p, "RETURN", Map.of("terms", "3 day returns", "periodDays", 3)));
        JsonNode history = expectOk(api.get("/api/v1/providers/me/policies/RETURN/versions", p)).data();
        assertThat(history.size()).isEqualTo(2);
        assertThat(history.get(0).path("versionNo").asInt()).isEqualTo(2);
        assertThat(history.get(1).path("terms").asText()).isEqualTo("7 day returns");

        JsonNode snapshots = expectOk(api.get("/api/v1/orders/" + orderId + "/policies", customer)).data();
        JsonNode ret = null;
        for (JsonNode n : snapshots) if (n.path("type").asText().equals("RETURN")) ret = n;
        assertThat(ret).isNotNull();
        assertThat(ret.path("periodDays").asInt()).isEqualTo(7);
        assertThat(ret.path("versionNo").asInt()).isEqualTo(1);
        assertThat(ret.path("source").asText()).isEqualTo("PROVIDER");

        JsonNode current = expectOk(api.get("/api/v1/providers/" + p.providerId() + "/policies", customer)).data();
        boolean seen = false;
        for (JsonNode n : current) if (n.path("type").asText().equals("RETURN")) { seen = true; assertThat(n.path("periodDays").asInt()).isEqualTo(3); }
        assertThat(seen).isTrue();
    }

    @Test
    void providerPoliciesCannotWeakenPlatformRules() {
        Actor p = api.verifiedProvider(List.of(api.newService()));
        Result r = publish(p, "CANCELLATION", Map.of("terms", "No refunds ever", "feePercent", 90));
        assertThat(r.status()).isEqualTo(422);
        assertThat(r.code()).isEqualTo("POLICY_VIOLATES_PLATFORM_RULES");
        assertThat(publish(p, "CANCELLATION", Map.of("terms", "", "feePercent", 10)).code()).isEqualTo("VALIDATION_FAILED");
        expectStatus(201, publish(p, "CANCELLATION", Map.of("terms", "20% fee within 24h", "feePercent", 20, "freeCancellationHours", 24)));
        assertThat(api.post("/api/v1/providers/me/policies/RETURN/versions", api.customer(), Map.of("terms", "x")).status()).isEqualTo(403);
    }

    @Test
    void lateCustomerCancellationAppliesTheSnapshotFee() {
        long s = api.newService();
        Actor p = api.verifiedProvider(List.of(s));
        expectStatus(201, publish(p, "CANCELLATION", Map.of("terms", "20% fee inside 24h", "feePercent", 20, "freeCancellationHours", 24)));
        Actor customer = api.customer();
        long orderId = api.order(customer, List.of(s), p.providerId());
        Map<String, Object> offer = new LinkedHashMap<>();
        offer.put("orderId", orderId);
        offer.put("description", "Service");
        offer.put("laborCost", new BigDecimal("500.00"));
        offer.put("estimatedDurationMinutes", 60);
        offer.put("proposedAppointmentAt", Instant.now().plus(Duration.ofHours(5)).truncatedTo(ChronoUnit.MINUTES).toString());
        long responseId = expectStatus(201, api.post("/api/v1/responses", p, offer)).data().path("id").asLong();
        long paymentId = expectOk(api.post("/api/v1/responses/" + responseId + "/accept", customer, null)).data().path("paymentId").asLong();
        payCash(customer, p, paymentId);

        // a later policy change must not affect this order
        expectStatus(201, publish(p, "CANCELLATION", Map.of("terms", "free", "feePercent", 0)));
        Result canceled = expectOk(api.post("/api/v1/orders/" + orderId + "/cancel", customer, Map.of("reason", "late")));
        assertThat(canceled.data().path("refundIds").size()).isEqualTo(1);
        BigDecimal refund = jdbc.queryForObject("SELECT amount FROM refunds WHERE order_id = ?", BigDecimal.class, orderId);
        assertThat(refund).isEqualByComparingTo("400.00");
        assertThat(jdbc.queryForObject("SELECT status FROM appointments WHERE order_id = ?", String.class, orderId)).isEqualTo("CANCELED");
    }

    @Test
    void providerNoShowCancelsAndRefundsFully() {
        long s = api.newService();
        Actor p = api.verifiedProvider(List.of(s));
        Actor customer = api.customer();
        long[] ids = api.acceptedOrder(customer, p, List.of(s), new BigDecimal("300"));
        long orderId = ids[0];
        Instant slot = Instant.now().plus(Duration.ofHours(3)).truncatedTo(ChronoUnit.MINUTES);
        expectOk(api.put("/api/v1/orders/" + orderId + "/appointment", customer, Map.of("startAt", slot.toString())));
        payCash(customer, p, ids[2]);

        Result early = api.post("/api/v1/orders/" + orderId + "/appointment/no-show", customer, Map.of("reason", "not there"));
        assertThat(early.code()).isEqualTo("NO_SHOW_TOO_EARLY");

        clock.advance(Duration.ofHours(4));
        JsonNode appt = expectOk(api.post("/api/v1/orders/" + orderId + "/appointment/no-show", customer, Map.of("reason", "workshop closed"))).data();
        assertThat(appt.path("status").asText()).isEqualTo("PROVIDER_NO_SHOW");
        assertThat(appt.path("history").size()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId)).isEqualTo("CANCELED");
        Map<String, Object> refund = jdbc.queryForMap("SELECT amount, source, status FROM refunds WHERE order_id = ?", orderId);
        assertThat((BigDecimal) refund.get("amount")).isEqualByComparingTo("300");
        assertThat(refund.get("source")).isEqualTo("NO_SHOW");
        assertThat(refund.get("status")).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM appointments WHERE order_id = ?", Integer.class, orderId)).isEqualTo(1);
    }

    @Test
    void appointmentsRespectWorkingHoursAndKeepRescheduleHistory() {
        long s = api.newService();
        Actor p = api.verifiedProvider(List.of(s));
        long branchId = jdbc.queryForObject("SELECT id FROM provider_branches WHERE provider_id = ?", Long.class, p.providerId());
        List<Map<String, Object>> hours = new java.util.ArrayList<>();
        for (int d = 1; d <= 7; d++) hours.add(Map.of("dayOfWeek", d, "opensAt", "09:00", "closesAt", "17:00"));
        expectOk(api.patch("/api/v1/providers/me/branches/" + branchId, p, Map.of("workingHours", hours)));
        Actor customer = api.customer();
        long orderId = api.acceptedOrder(customer, p, List.of(s), new BigDecimal("100"))[0];

        Result night = api.put("/api/v1/orders/" + orderId + "/appointment", customer, Map.of("branchId", branchId, "startAt", tomorrowAt(22).toString()));
        assertThat(night.status()).isEqualTo(422);
        assertThat(night.code()).isEqualTo("APPOINTMENT_OUTSIDE_WORKING_HOURS");
        expectOk(api.put("/api/v1/orders/" + orderId + "/appointment", customer, Map.of("branchId", branchId, "startAt", tomorrowAt(10).toString())));
        assertThat(api.put("/api/v1/orders/" + orderId + "/appointment", p, Map.of("startAt", tomorrowAt(12).toString())).code()).isEqualTo("VALIDATION_FAILED");
        JsonNode rescheduled = expectOk(api.put("/api/v1/orders/" + orderId + "/appointment", p,
                Map.of("startAt", tomorrowAt(12).toString(), "reason", "Busy at 10"))).data();
        assertThat(rescheduled.path("status").asText()).isEqualTo("RESCHEDULED");
        assertThat(rescheduled.path("history").size()).isEqualTo(2);
        assertThat(api.get("/api/v1/orders/" + orderId + "/appointment", api.customer()).status()).isEqualTo(403);
        assertThat(LocalTime.of(10, 0)).isNotNull();
    }

    @Test
    void warrantyIsIssuedFromTheSnapshotAndClaimsCanEscalate() {
        long s = api.newService();
        Actor p = api.verifiedProvider(List.of(s));
        expectStatus(201, publish(p, "WARRANTY", Map.of("terms", "30 days on labour", "periodDays", 30)));
        expectStatus(201, publish(p, "RETURN", Map.of("terms", "7 days", "periodDays", 7)));
        Actor customer = api.customer();
        long[] ids = api.acceptedOrder(customer, p, List.of(s), new BigDecimal("200"));
        long orderId = ids[0];
        payCash(customer, p, ids[2]);
        expectOk(api.post("/api/v1/orders/" + orderId + "/start", p, null));
        expectOk(api.post("/api/v1/orders/" + orderId + "/mark-done", p, null));
        expectOk(api.post("/api/v1/orders/" + orderId + "/confirm-completion", customer, null));

        JsonNode warranty = expectOk(api.get("/api/v1/orders/" + orderId + "/warranty", customer)).data();
        assertThat(warranty.path("validToday").asBoolean()).isTrue();
        long claimId = expectStatus(201, api.post("/api/v1/orders/" + orderId + "/warranty/claims", customer,
                Map.of("description", "Noise came back"))).data().path("id").asLong();
        expectOk(api.post("/api/v1/warranty-claims/" + claimId + "/decision", p, Map.of("accept", false, "response", "Not covered")));
        JsonNode escalated = expectOk(api.post("/api/v1/warranty-claims/" + claimId + "/escalate", customer, null)).data();
        assertThat(escalated.path("status").asText()).isEqualTo("ESCALATED");
        assertThat(jdbc.queryForObject("SELECT type FROM disputes WHERE id = ?", String.class, escalated.path("disputeId").asLong()))
                .isEqualTo("WARRANTY_DISPUTE");

        long returnId = expectStatus(201, api.post("/api/v1/orders/" + orderId + "/returns", customer,
                Map.of("description", "Unused filter"))).data().path("id").asLong();
        expectOk(api.post("/api/v1/returns/" + returnId + "/decision", p, Map.of("approve", true)));
        JsonNode received = expectOk(api.post("/api/v1/returns/" + returnId + "/received", p, Map.of("refundAmount", 20))).data();
        assertThat(received.path("refundId").asLong()).isPositive();

        clock.advance(Duration.ofDays(8));
        assertThat(api.post("/api/v1/orders/" + orderId + "/returns", customer, Map.of("description", "late")).code()).isEqualTo("RETURN_WINDOW_CLOSED");
    }

    @Test
    void suspendingAProviderWithdrawsItsPendingOffers() {
        long s = api.newService();
        Actor p = api.verifiedProvider(List.of(s));
        Actor customer = api.customer();
        long orderId = api.order(customer, List.of(s), null);
        long responseId = api.offer(p, orderId, new BigDecimal("90"));
        Actor busy = api.verifiedProvider(List.of(s));
        api.acceptedOrder(api.customer(), busy, List.of(s), new BigDecimal("50"));

        JsonNode result = expectOk(api.post("/api/v1/admin/providers/" + p.providerId() + "/status", api.admin(),
                Map.of("status", "SUSPENDED", "reason", "fraud check"))).data();
        assertThat(result.path("withdrawnOffers").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM order_responses WHERE id = ?", String.class, responseId)).isEqualTo("CANCELED");
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId)).isEqualTo("QUOTING");
        assertThat(api.submitOffer(p, api.order(api.customer(), List.of(s), null), BigDecimal.TEN, null).status()).isIn(403, 422);

        JsonNode busyResult = expectOk(api.post("/api/v1/admin/providers/" + busy.providerId() + "/status", api.admin(),
                Map.of("status", "SUSPENDED", "reason", "x"))).data();
        assertThat(busyResult.path("assignedOrdersNeedingAction").asLong()).isEqualTo(1);
    }

    @Test
    void providersCannotTouchAnotherProvidersResources() {
        Actor a = api.verifiedProvider(List.of(api.newService()));
        Actor b = api.verifiedProvider(List.of(api.newService()));
        long branchOfA = jdbc.queryForObject("SELECT id FROM provider_branches WHERE provider_id = ?", Long.class, a.providerId());
        assertThat(api.patch("/api/v1/providers/me/branches/" + branchOfA, b, Map.of("name", "hijacked")).status()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT name FROM provider_branches WHERE id = ?", String.class, branchOfA)).isEqualTo("Main");
        long docId = expectStatus(201, api.multipart("/api/v1/providers/me/documents", a, "id.png", "image/png", TestApi.PNG,
                Map.of("type", "NATIONAL_ID_FRONT"))).data().path("id").asLong();
        assertThat(api.rawStatus(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/providers/documents/" + docId + "/file"), b))
                .isEqualTo(403);
        assertThat(api.rawStatus(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/providers/documents/" + docId + "/file"), a))
                .isEqualTo(200);
        assertThat(api.post("/api/v1/admin/providers/" + b.providerId() + "/verification", a, Map.of("decision", "REJECTED")).status()).isEqualTo(403);
    }

    @Test
    void orderChatIsLimitedToItsParties() {
        long s = api.newService();
        Actor p = api.verifiedProvider(List.of(s));
        Actor customer = api.customer();
        long orderId = api.acceptedOrder(customer, p, List.of(s), new BigDecimal("80"))[0];
        long conversationId = expectOk(api.get("/api/v1/orders/" + orderId + "/conversation", customer)).data().path("id").asLong();
        expectStatus(201, api.post("/api/v1/conversations/" + conversationId + "/messages", customer, Map.of("text", "Hello")));
        expectStatus(201, api.post("/api/v1/conversations/" + conversationId + "/messages", p, Map.of("text", "Hi")));
        Actor intruder = api.customer();
        assertThat(api.get("/api/v1/conversations/" + conversationId + "/messages", intruder).status()).isEqualTo(403);
        assertThat(api.post("/api/v1/conversations/" + conversationId + "/messages", intruder, Map.of("text", "spam")).status()).isEqualTo(403);
        assertThat(api.post("/api/v1/conversations/" + conversationId + "/calls", intruder, null).status()).isEqualTo(403);
        JsonNode call = expectStatus(201, api.post("/api/v1/conversations/" + conversationId + "/calls?type=VIDEO", customer, null)).data();
        assertThat(call.path("call").path("channelName").asText()).isEqualTo("conv_" + conversationId);
        assertThat(call.path("token").path("uid").asLong()).isEqualTo(customer.userId());
        long callId = call.path("call").path("id").asLong();
        assertThat(api.post("/api/v1/calls/" + callId + "/accept", customer, null).status()).isEqualTo(403);
        expectOk(api.post("/api/v1/calls/" + callId + "/accept", p, null));
        assertThat(api.post("/api/v1/calls/" + callId + "/token", intruder, null).status()).isEqualTo(403);
        JsonNode messages = expectOk(api.get("/api/v1/conversations/" + conversationId + "/messages", p)).data();
        assertThat(messages.path("totalItems").asInt()).isEqualTo(3);
    }
}
