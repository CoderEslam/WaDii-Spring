package com.doubleclick.wadii;

import com.doubleclick.wadii.support.IntegrationTest;
import com.doubleclick.wadii.support.TestApi.Actor;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.doubleclick.wadii.support.TestApi.money;
import static org.assertj.core.api.Assertions.assertThat;

/** Multi-service requests, per-item acceptance, completion and cash (business rules sections 4, 6, 7, 9). */
class MarketplaceFlowSpecTest extends IntegrationTest {

    @Test
    void providerOfferingOnlyOneRequestedServiceIsInvitedAndSeesOnlyThatItem() {
        Actor customer = api.customer();
        Actor batteries = api.provider("BATTERIES");
        JsonNode request = api.request(customer, "BATTERIES", "OIL_CHANGE");
        long requestId = request.path("id").asLong();

        var view = api.get("/api/v1/provider/requests/" + requestId, batteries).ok().data();
        assertThat(view.path("items")).hasSize(1);
        assertThat(view.path("items").get(0).path("category").path("code").asText()).isEqualTo("BATTERIES");
        assertThat(view.path("targetStatus").asText()).isEqualTo("VIEWED");

        long oilItem = api.itemId(request, "OIL_CHANGE");
        var refused = api.post("/api/v1/provider/requests/" + requestId + "/offer", batteries,
                Map.of("branchId", api.branchId(batteries), "lines", List.of(api.line(oilItem, 200, false))));
        assertThat(refused.code()).isEqualTo("CATEGORY_NOT_OFFERED");
    }

    @Test
    void customerAcceptsDifferentProvidersForDifferentItemsAndOnlySameItemCompetitorsAreDeclined() {
        Actor customer = api.customer();
        Actor a = api.provider("BATTERIES", "OIL_CHANGE");
        Actor b = api.provider("BATTERIES", "OIL_CHANGE");
        JsonNode request = api.request(customer, "BATTERIES", "OIL_CHANGE");
        long requestId = request.path("id").asLong();
        long battery = api.itemId(request, "BATTERIES");
        long oil = api.itemId(request, "OIL_CHANGE");

        JsonNode offerA = api.offer(a, requestId, List.of(api.line(battery, 1000, true), api.line(oil, 300, false)));
        JsonNode offerB = api.offer(b, requestId, List.of(api.line(battery, 1200, true), api.line(oil, 250, false)));

        var detail = api.get("/api/v1/requests/" + requestId, customer).ok().data();
        assertThat(detail.path("status").asText()).isEqualTo("OFFERS_AVAILABLE");
        assertThat(detail.path("editable").asBoolean()).isFalse();

        JsonNode batteryOrder = api.accept(customer, requestId, battery, api.lineId(offerA, battery));
        assertThat(batteryOrder.path("provider").path("id").asLong()).isEqualTo(a.providerId());

        detail = api.get("/api/v1/requests/" + requestId, customer).ok().data();
        assertThat(detail.path("status").asText()).isEqualTo("PARTIALLY_MATCHED");
        JsonNode bLines = api.get("/api/v1/provider/offers", b).ok().data().path("items").get(0).path("lines");
        assertThat(lineStatus(bLines, battery)).isEqualTo("DECLINED");
        assertThat(lineStatus(bLines, oil)).isEqualTo("PENDING");

        JsonNode oilOrder = api.accept(customer, requestId, oil, api.lineId(offerB, oil));
        assertThat(oilOrder.path("provider").path("id").asLong()).isEqualTo(b.providerId());
        detail = api.get("/api/v1/requests/" + requestId, customer).ok().data();
        assertThat(detail.path("status").asText()).isEqualTo("CLOSED");
        assertThat(batteryOrder.path("id").asLong()).isNotEqualTo(oilOrder.path("id").asLong());
    }

    @Test
    void serverComputesTotalsAndSnapshotsWarrantyAndPolicy() {
        Actor customer = api.customer();
        Actor p = api.provider("TIRES");
        JsonNode request = api.request(customer, "TIRES");
        long requestId = request.path("id").asLong();
        long item = api.itemId(request, "TIRES");
        var line = new java.util.LinkedHashMap<>(api.line(item, 1000, true));
        line.put("discount", 100);
        line.put("customerTotal", 1);
        JsonNode offer = api.offer(p, requestId, List.of(line));
        var pricing = offer.path("lines").get(0).path("pricing");
        assertThat(money(pricing.path("partsTotal"))).isEqualByComparingTo("50.00");
        assertThat(money(pricing.path("providerTotal"))).isEqualByComparingTo("1000.00");
        assertThat(money(pricing.path("customerTotal"))).isEqualByComparingTo("1000.00");

        api.put("/api/v1/provider/policy", p, Map.of("cancellationTerms", "v1 cancel", "noShowTerms", "v1 no-show",
                "serviceConditions", "v1 conditions", "returnTerms", "v1 returns")).ok();
        JsonNode order = api.accept(customer, requestId, item, api.lineId(offer, item));
        api.put("/api/v1/provider/policy", p, Map.of("cancellationTerms", "v2 cancel", "noShowTerms", "v2", "serviceConditions", "v2",
                "returnTerms", "v2")).ok();

        JsonNode again = api.get("/api/v1/orders/" + order.path("id").asLong(), customer).ok().data();
        assertThat(again.path("policySnapshot").path("cancellationTerms").asText()).isEqualTo("v1 cancel");
        assertThat(again.path("warranty").path("durationDays").asInt()).isEqualTo(90);
        assertThat(again.path("cashStatus").asText()).isEqualTo("NOT_DUE");
    }

    @Test
    void completionNeedsCustomerConfirmationAndCashIsIndependent() {
        Actor customer = api.customer();
        Actor p = api.provider("REPAIRS");
        JsonNode order = acceptedOrder(customer, p, "REPAIRS", true);
        long id = order.path("id").asLong();

        assertThat(order.path("actions").toString()).doesNotContain("CONFIRM_COMPLETION");
        api.post("/api/v1/orders/" + id + "/start", p, null).ok();
        var afterCash = api.post("/api/v1/orders/" + id + "/cash/report", p, Map.of("amount", 200)).ok().data();
        assertThat(afterCash.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(afterCash.path("cashStatus").asText()).isEqualTo("PARTIAL");

        var marked = api.post("/api/v1/orders/" + id + "/mark-completed", p, Map.of("note", "done")).ok().data();
        assertThat(marked.path("status").asText()).isEqualTo("PROVIDER_MARKED_COMPLETED");

        clock.advance(Duration.ofDays(10));
        assertThat(api.get("/api/v1/orders/" + id, customer).ok().data().path("status").asText())
                .as("no automatic completion").isEqualTo("PROVIDER_MARKED_COMPLETED");
        clock.reset();

        assertThat(api.post("/api/v1/orders/" + id + "/confirm-completion", p, null).status()).isEqualTo(403);
        var done = api.post("/api/v1/orders/" + id + "/confirm-completion", customer, null).ok().data();
        assertThat(done.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(done.path("cashStatus").asText()).as("cash not inferred from completion").isEqualTo("PARTIAL");
        assertThat(done.path("invoiceNumber").asText()).startsWith("WD-");
        assertThat(done.path("warrantyId").isNumber()).isTrue();
        assertThat(done.path("actions").toString()).contains("RATE", "CONFIRM_CASH");

        api.post("/api/v1/orders/" + id + "/cash/confirm", customer, null).ok();
        var full = api.post("/api/v1/orders/" + id + "/cash/report", p, Map.of("amount", done.path("amountDue").decimalValue())).ok().data();
        assertThat(full.path("cashStatus").asText()).isEqualTo("REPORTED_COLLECTED");
        assertThat(api.post("/api/v1/orders/" + id + "/cash/confirm", customer, null).ok().data().path("cashStatus").asText())
                .isEqualTo("CONFIRMED");

        api.post("/api/v1/orders/" + id + "/rating", customer, Map.of("stars", 5, "comment", "Great")).ok();
        assertThat(api.post("/api/v1/orders/" + id + "/rating", customer, Map.of("stars", 4)).code()).isEqualTo("RATING_ALREADY_EXISTS");
        assertThat(api.get("/api/v1/providers/" + p.providerId(), null).ok().data().path("ratingCount").asInt()).isEqualTo(1);
    }

    @Test
    void complaintInsteadOfConfirmationOpensADispute() {
        Actor customer = api.customer();
        Actor p = api.provider("CAR_WASH");
        JsonNode order = acceptedOrder(customer, p, "CAR_WASH", false);
        long id = order.path("id").asLong();
        api.post("/api/v1/orders/" + id + "/start", p, null).ok();
        api.post("/api/v1/orders/" + id + "/mark-completed", p, null).ok();
        var dispute = api.post("/api/v1/orders/" + id + "/complain", customer, Map.of("description", "Still dirty")).ok().data();
        assertThat(dispute.path("type").asText()).isEqualTo("SERVICE_QUALITY");
        assertThat(api.get("/api/v1/orders/" + id, customer).ok().data().path("status").asText()).isEqualTo("DISPUTED");
        assertThat(api.get("/api/v1/disputes/" + dispute.path("id").asLong(), p).ok().data().path("status").asText()).isEqualTo("OPEN");
    }

    @Test
    void commissionIsSnapshottedAndRecordedAsDueForTheProvider() {
        api.config("COMMISSION", Map.of("mode", "PERCENT", "value", 10, "payer", "CUSTOMER"));
        try {
            Actor customer = api.customer();
            Actor p = api.provider("ACCESSORIES");
            JsonNode request = api.request(customer, "ACCESSORIES");
            long requestId = request.path("id").asLong();
            long item = api.itemId(request, "ACCESSORIES");
            JsonNode offer = api.offer(p, requestId, List.of(api.line(item, 900, false)));
            var pricing = offer.path("lines").get(0).path("pricing");
            assertThat(money(pricing.path("providerTotal"))).isEqualByComparingTo("1000.00");
            assertThat(money(pricing.path("commissionAmount"))).isEqualByComparingTo("100.00");
            assertThat(money(pricing.path("customerTotal"))).isEqualByComparingTo("1100.00");

            api.config("COMMISSION", Map.of("mode", "NONE", "value", 0, "payer", "PROVIDER"));
            JsonNode order = api.accept(customer, requestId, item, api.lineId(offer, item));
            assertThat(money(order.path("pricing").path("commissionAmount"))).as("accepted terms keep their version")
                    .isEqualByComparingTo("100.00");
            long id = order.path("id").asLong();
            api.post("/api/v1/orders/" + id + "/start", p, null).ok();
            api.post("/api/v1/orders/" + id + "/mark-completed", p, null).ok();
            api.post("/api/v1/orders/" + id + "/confirm-completion", customer, null).ok();

            var ledger = api.get("/api/v1/provider/ledger?type=COMMISSION", p).ok().data().path("items");
            assertThat(ledger).hasSize(1);
            assertThat(money(ledger.get(0).path("amount"))).isEqualByComparingTo("100.00");
            assertThat(ledger.get(0).path("status").asText()).isEqualTo("DUE");
            var reported = api.post("/api/v1/provider/ledger/" + ledger.get(0).path("id").asLong() + "/report-paid", p,
                    Map.of("method", "CASH")).ok().data();
            assertThat(reported.path("status").asText()).isEqualTo("REPORTED_PAID");
            api.post("/api/v1/admin/ledger/" + ledger.get(0).path("id").asLong() + "/settle", api.admin(), Map.of("method", "CASH")).ok();
        } finally {
            api.config("COMMISSION", Map.of("mode", "NONE", "value", 0, "payer", "PROVIDER"));
        }
    }

    @Test
    void additionalChargesApplyOnlyAfterCustomerApproval() {
        Actor customer = api.customer();
        Actor p = api.provider("SPARE_PARTS");
        JsonNode order = acceptedOrder(customer, p, "SPARE_PARTS", false);
        long id = order.path("id").asLong();
        BigDecimal before = order.path("amountDue").decimalValue();
        api.post("/api/v1/orders/" + id + "/start", p, null).ok();
        long charge = api.post("/api/v1/orders/" + id + "/charges", p, Map.of("description", "Extra filter", "amount", 150))
                .ok().data().path("id").asLong();
        assertThat(api.get("/api/v1/orders/" + id, customer).ok().data().path("amountDue").decimalValue()).isEqualByComparingTo(before);
        api.post("/api/v1/orders/" + id + "/charges/" + charge + "/approve", customer, null).ok();
        assertThat(api.get("/api/v1/orders/" + id, customer).ok().data().path("amountDue").decimalValue())
                .isEqualByComparingTo(before.add(new BigDecimal("150")));
    }

    static String lineStatus(JsonNode lines, long itemId) {
        List<String> found = new ArrayList<>();
        for (JsonNode l : lines) if (l.path("serviceItemId").asLong() == itemId) found.add(l.path("status").asText());
        return found.get(found.size() - 1);
    }

    private JsonNode acceptedOrder(Actor customer, Actor provider, String category, boolean warranty) {
        JsonNode request = api.request(customer, category);
        long requestId = request.path("id").asLong();
        long item = api.itemId(request, category);
        JsonNode offer = api.offer(provider, requestId, List.of(api.line(item, 400, warranty)));
        return api.accept(customer, requestId, item, api.lineId(offer, item));
    }
}
