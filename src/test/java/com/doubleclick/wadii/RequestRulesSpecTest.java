package com.doubleclick.wadii;

import com.doubleclick.wadii.request.ExpirationJobs;
import com.doubleclick.wadii.support.IntegrationTest;
import com.doubleclick.wadii.support.TestApi.Actor;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** Request validation, editing, deadlines and acceptance rules (business rules sections 4.4, 6.3, 10). */
class RequestRulesSpecTest extends IntegrationTest {

    @Autowired
    ExpirationJobs jobs;

    @Test
    void vehicleIsRequiredOnlyWhenACategoryNeedsIt() {
        Actor customer = api.customer();
        Map<String, Object> wash = new LinkedHashMap<>();
        wash.put("items", List.of(Map.of("categoryId", api.categoryId("CAR_WASH"))));
        wash.put("areaId", 1);
        wash.put("address", "Heliopolis");
        api.post("/api/v1/requests", customer, wash).ok();

        Map<String, Object> oil = new LinkedHashMap<>(wash);
        oil.put("items", List.of(Map.of("categoryId", api.categoryId("OIL_CHANGE"))));
        var r = api.post("/api/v1/requests", customer, oil);
        assertThat(r.status()).isEqualTo(422);
        assertThat(r.code()).isEqualTo("VEHICLE_REQUIRED");
    }

    @Test
    void savedVehicleBecomesTheDefaultAndRequestsKeepASnapshot() {
        Actor customer = api.customer();
        JsonNode request = api.request(customer, "TIRES");
        var vehicles = api.get("/api/v1/vehicles", customer).ok().data();
        assertThat(vehicles.get(0).path("label").asText()).isEqualTo("Chevrolet Lanos 2016");
        api.put("/api/v1/vehicles/" + vehicles.get(0).path("id").asLong(), customer, Map.of("make", "Kia", "model", "Rio", "year", 2020)).ok();
        var detail = api.get("/api/v1/requests/" + request.path("id").asLong(), customer).ok().data();
        assertThat(detail.path("vehicle").path("label").asText()).isEqualTo("Chevrolet Lanos 2016");
    }

    @Test
    void requestCanBeEditedOnlyBeforeAnyOffer() {
        Actor customer = api.customer();
        Actor p = api.provider("BATTERIES", "TIRES");
        JsonNode request = api.request(customer, "BATTERIES");
        long id = request.path("id").asLong();
        assertThat(request.path("editable").asBoolean()).isTrue();

        Map<String, Object> edit = new LinkedHashMap<>();
        edit.put("vehicle", Map.of("make", "Chevrolet", "model", "Lanos", "year", 2016));
        edit.put("items", List.of(Map.of("categoryId", api.categoryId("BATTERIES")), Map.of("categoryId", api.categoryId("TIRES"))));
        edit.put("areaId", 1);
        edit.put("address", "Nasr City 2");
        JsonNode edited = api.put("/api/v1/requests/" + id, customer, edit).ok().data();
        assertThat(edited.path("items")).hasSize(2);

        long item = api.itemId(edited, "TIRES");
        api.offer(p, id, List.of(api.line(item, 500, false)));
        assertThat(api.put("/api/v1/requests/" + id, customer, edit).code()).isEqualTo("REQUEST_NOT_EDITABLE");
    }

    @Test
    void deadlineExpiresOpenItemsButAcceptedOrdersContinue() {
        Actor customer = api.customer();
        Actor p = api.provider("BATTERIES", "OIL_CHANGE");
        JsonNode request = api.request(customer, "BATTERIES", "OIL_CHANGE");
        long id = request.path("id").asLong();
        long battery = api.itemId(request, "BATTERIES");
        long oil = api.itemId(request, "OIL_CHANGE");
        JsonNode offer = api.offer(p, id, List.of(api.line(battery, 800, false), api.line(oil, 200, false)));
        JsonNode order = api.accept(customer, id, battery, api.lineId(offer, battery));

        clock.advance(Duration.ofHours(49));
        jobs.expireOffers();
        jobs.expireRequests();
        var detail = api.get("/api/v1/requests/" + id, customer).ok().data();
        assertThat(detail.path("status").asText()).isEqualTo("CLOSED");
        for (JsonNode i : detail.path("items")) {
            assertThat(i.path("status").asText()).isEqualTo(i.path("id").asLong() == battery ? "ACCEPTED" : "EXPIRED");
        }
        assertThat(api.get("/api/v1/orders/" + order.path("id").asLong(), customer).ok().data().path("status").asText())
                .isEqualTo("ACCEPTED");
    }

    @Test
    void offerValidityIsBoundedAndNeverPassesTheDeadline() {
        Actor customer = api.customer();
        Actor p = api.provider("TIRES");
        JsonNode request = api.request(customer, "TIRES");
        long id = request.path("id").asLong();
        long item = api.itemId(request, "TIRES");
        var tooLong = api.post("/api/v1/provider/requests/" + id + "/offer", p,
                Map.of("branchId", api.branchId(p), "validHours", 100, "lines", List.of(api.line(item, 500, false))));
        assertThat(tooLong.code()).isEqualTo("INVALID_EXPIRY");
        var offer = api.post("/api/v1/provider/requests/" + id + "/offer", p,
                Map.of("branchId", api.branchId(p), "validHours", 48, "lines", List.of(api.line(item, 500, false)))).ok().data();
        assertThat(offer.path("expiresAt").asText()).isLessThanOrEqualTo(request.path("responseDeadline").asText());
    }

    @Test
    void warrantyChoiceIsMandatoryPerLine() {
        Actor customer = api.customer();
        Actor p = api.provider("REPAIRS");
        JsonNode request = api.request(customer, "REPAIRS");
        long item = api.itemId(request, "REPAIRS");
        Map<String, Object> line = new LinkedHashMap<>(api.line(item, 500, false));
        line.remove("warranty");
        var r = api.post("/api/v1/provider/requests/" + request.path("id").asLong() + "/offer", p,
                Map.of("branchId", api.branchId(p), "lines", List.of(line)));
        assertThat(r.code()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void providerCannotBeServedByItsOwnOfferAndExplicitProviderMustBeEligible() {
        Actor p = api.provider("CAR_WASH");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(Map.of("categoryId", api.categoryId("CAR_WASH"))));
        body.put("areaId", 1);
        body.put("address", "Maadi");
        body.put("providerId", p.providerId());
        assertThat(api.post("/api/v1/requests", p, body).code()).isEqualTo("SELF_DEALING_FORBIDDEN");

        Actor customer = api.customer();
        Map<String, Object> wrongCategory = new LinkedHashMap<>(body);
        wrongCategory.put("items", List.of(Map.of("categoryId", api.categoryId("BATTERIES"), "description", "x")));
        wrongCategory.put("vehicle", Map.of("make", "Fiat", "model", "128", "year", 1990));
        assertThat(api.post("/api/v1/requests", customer, wrongCategory).code()).isEqualTo("PROVIDER_NOT_ELIGIBLE");

        JsonNode explicit = api.post("/api/v1/requests", customer, body).ok().data();
        assertThat(explicit.path("invitedProviders").asInt()).isEqualTo(1);
    }

    @Test
    void onlyOneOfTwoConcurrentAcceptancesForTheSameItemWins() throws Exception {
        Actor customer = api.customer();
        Actor a = api.provider("BATTERIES");
        Actor b = api.provider("BATTERIES");
        JsonNode request = api.request(customer, "BATTERIES");
        long id = request.path("id").asLong();
        long item = api.itemId(request, "BATTERIES");
        long lineA = api.lineId(api.offer(a, id, List.of(api.line(item, 700, false))), item);
        long lineB = api.lineId(api.offer(b, id, List.of(api.line(item, 650, false))), item);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (long line : new long[]{lineA, lineB}) {
                calls.add(() -> api.post("/api/v1/requests/" + id + "/items/" + item + "/accept", customer, Map.of("lineId", line)).status());
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : pool.invokeAll(calls)) statuses.add(f.get());
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void appointmentMustRespectWorkingHoursAndCapacity() {
        Actor customer = api.customer();
        Actor p = api.provider("CAR_WASH");
        long branch = api.branchId(p);
        api.put("/api/v1/provider/branches/" + branch, p, api.branch(1)).ok();
        List<Map<String, Object>> hours = new ArrayList<>();
        for (int d = 1; d <= 7; d++) hours.add(Map.of("dayOfWeek", d, "opensAt", "09:00", "closesAt", "18:00"));
        api.put("/api/v1/provider/branches/" + branch + "/hours", p, Map.of("hours", hours)).ok();

        var slot = java.time.ZonedDateTime.now(com.doubleclick.wadii.common.util.Times.CAIRO).plusDays(2)
                .withHour(10).withMinute(0).withSecond(0).withNano(0).toInstant();
        var night = slot.plus(Duration.ofHours(12));

        JsonNode r1 = washRequest(customer);
        long item1 = api.itemId(r1, "CAR_WASH");
        long line1 = api.lineId(api.offer(p, r1.path("id").asLong(), List.of(api.line(item1, 100, false))), item1);
        var outside = api.post("/api/v1/requests/" + r1.path("id").asLong() + "/items/" + item1 + "/accept", customer,
                Map.of("lineId", line1, "appointmentStart", night.toString()));
        assertThat(outside.code()).isEqualTo("OUTSIDE_WORKING_HOURS");
        api.accept(customer, r1.path("id").asLong(), item1, line1, slot);

        Actor secondCustomer = api.customer();
        JsonNode r2 = washRequest(secondCustomer);
        long item2 = api.itemId(r2, "CAR_WASH");
        long line2 = api.lineId(api.offer(p, r2.path("id").asLong(), List.of(api.line(item2, 100, false))), item2);
        var full = api.post("/api/v1/requests/" + r2.path("id").asLong() + "/items/" + item2 + "/accept",
                secondCustomer, Map.of("lineId", line2, "appointmentStart", slot.toString()));
        assertThat(full.code()).isEqualTo("SLOT_FULL");
    }

    private JsonNode washRequest(Actor customer) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(Map.of("categoryId", api.categoryId("CAR_WASH"))));
        body.put("areaId", 1);
        body.put("address", "Zamalek");
        return api.post("/api/v1/requests", customer, body).ok().data();
    }
}
