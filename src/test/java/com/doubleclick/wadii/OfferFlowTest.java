package com.doubleclick.wadii;

import com.doubleclick.wadii.order.OrderJobs;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.doubleclick.wadii.support.TestApi.expectOk;
import static com.doubleclick.wadii.support.TestApi.expectStatus;
import static org.assertj.core.api.Assertions.assertThat;

class OfferFlowTest extends IntegrationTest {

    @Autowired
    TestApi api;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    OrderJobs jobs;
    @Autowired
    MutableClock clock;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private String status(String table, long id) {
        return jdbc.queryForObject("SELECT status FROM " + table + " WHERE id = ?", String.class, id);
    }

    @Test
    void validOfferIsPricedByTheServerAndMovesTheOrder() {
        long s = api.newService();
        Actor p = api.verifiedProvider(List.of(s));
        Actor customer = api.customer();
        long orderId = api.order(customer, List.of(s), null);
        Long partId = jdbc.queryForObject("SELECT id FROM order_spare_parts WHERE order_id = ?", Long.class, orderId);

        Result r = expectStatus(201, api.post("/api/v1/responses", p, Map.of("orderId", orderId, "description", "Oil + filter",
                "laborCost", new BigDecimal("200.00"), "discount", new BigDecimal("50.00"),
                "partPrices", List.of(Map.of("sparePartId", partId, "price", new BigDecimal("150.00"))),
                "providerId", 999999, "price", 1)));
        JsonNode offer = r.data();
        assertThat(offer.path("price").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(offer.path("provider").path("id").asLong()).isEqualTo(p.providerId());
        assertThat(offer.path("status").asText()).isEqualTo("PENDING");
        Instant expiresAt = Instant.parse(offer.path("expiresAt").asText());
        assertThat(expiresAt).isAfter(Instant.now().plus(Duration.ofHours(47)));
        assertThat(status("orders", orderId)).isEqualTo("RESPONSES_AVAILABLE");
        assertThat(jdbc.queryForObject("SELECT status FROM order_provider_targets WHERE order_id = ? AND provider_id = ?",
                String.class, orderId, p.providerId())).isEqualTo("RESPONDED");
    }

    @Test
    void duplicateAndUninvitedOffersAreRefused() {
        long s = api.newService();
        Actor p = api.verifiedProvider(List.of(s));
        Actor outsider = api.verifiedProvider(List.of(api.newService()));
        long orderId = api.order(api.customer(), List.of(s), null);
        api.offer(p, orderId, new BigDecimal("100"));
        Result dup = api.submitOffer(p, orderId, new BigDecimal("90"), null);
        assertThat(dup.status()).isEqualTo(409);
        assertThat(dup.code()).isEqualTo("RESPONSE_ALREADY_EXISTS");
        Result uninvited = api.submitOffer(outsider, orderId, new BigDecimal("90"), null);
        assertThat(uninvited.status()).isEqualTo(403);
        assertThat(uninvited.code()).isEqualTo("RESPONSE_NOT_TARGETED");
        assertThat(api.submitOffer(api.customer(), orderId, new BigDecimal("90"), null).status()).isEqualTo(403);
    }

    @Test
    void offerExpiryMustBeInsideTheAllowedWindow() {
        long s = api.newService();
        Actor p = api.verifiedProvider(List.of(s));
        long orderId = api.order(api.customer(), List.of(s), null);
        Result past = api.submitOffer(p, orderId, BigDecimal.TEN, Instant.now().minusSeconds(60).toString());
        assertThat(past.status()).isEqualTo(422);
        assertThat(past.code()).isEqualTo("INVALID_EXPIRY");
        assertThat(api.submitOffer(p, orderId, BigDecimal.TEN, Instant.now().plus(Duration.ofMinutes(30)).toString()).code()).isEqualTo("INVALID_EXPIRY");
        assertThat(api.submitOffer(p, orderId, BigDecimal.TEN, Instant.now().plus(Duration.ofDays(8)).toString()).code()).isEqualTo("INVALID_EXPIRY");
        Result ok = expectStatus(201, api.submitOffer(p, orderId, BigDecimal.TEN, Instant.now().plus(Duration.ofHours(2)).toString()));
        assertThat(Instant.parse(ok.data().path("expiresAt").asText())).isBefore(Instant.now().plus(Duration.ofHours(3)));
    }

    @Test
    void expiredOffersCannotBeAcceptedAndTheJobMarksThem() {
        long s = api.newService();
        Actor p = api.verifiedProvider(List.of(s));
        Actor customer = api.customer();
        long orderId = api.order(customer, List.of(s), null);
        long responseId = expectStatus(201, api.submitOffer(p, orderId, BigDecimal.TEN, Instant.now().plus(Duration.ofHours(1)).plusSeconds(30).toString()))
                .data().path("id").asLong();

        clock.advance(Duration.ofHours(2));
        Result accept = api.post("/api/v1/responses/" + responseId + "/accept", customer, null);
        assertThat(accept.status()).isEqualTo(409);
        assertThat(accept.code()).isEqualTo("OFFER_EXPIRED");

        jobs.expireOffers();
        assertThat(status("order_responses", responseId)).isEqualTo("EXPIRED");
        assertThat(status("orders", orderId)).isEqualTo("QUOTING");
        Integer notified = jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = 'RESPONSE_EXPIRED'",
                Integer.class, customer.userId());
        assertThat(notified).isEqualTo(1);
        assertThat(api.post("/api/v1/responses/" + responseId + "/accept", customer, null).code()).isEqualTo("OFFER_EXPIRED");
    }

    @Test
    void ordersExpireAtTheResponseDeadline() {
        long s = api.newService();
        Actor p = api.verifiedProvider(List.of(s));
        Actor late = api.verifiedProvider(List.of(s));
        Actor customer = api.customer();
        long orderId = api.order(customer, List.of(s), null);
        long responseId = api.offer(p, orderId, BigDecimal.TEN);

        clock.advance(Duration.ofHours(49));
        assertThat(api.submitOffer(late, orderId, BigDecimal.TEN, null).code()).isEqualTo("ORDER_DEADLINE_PASSED");
        jobs.expireOrders();
        assertThat(status("orders", orderId)).isEqualTo("EXPIRED");
        assertThat(status("order_responses", responseId)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_responses WHERE order_id = ?", Integer.class, orderId)).isEqualTo(1);
    }

    @Test
    void acceptingIsAtomicAndKeepsTheOthersAsDeclinedHistory() {
        long s = api.newService();
        Actor a = api.verifiedProvider(List.of(s));
        Actor b = api.verifiedProvider(List.of(s));
        Actor c = api.verifiedProvider(List.of(s));
        Actor customer = api.customer();
        long orderId = api.order(customer, List.of(s), null);
        long ra = api.offer(a, orderId, new BigDecimal("500"));
        long rb = api.offer(b, orderId, new BigDecimal("450"));
        long rc = api.offer(c, orderId, new BigDecimal("400"));

        JsonNode sorted = expectOk(api.get("/api/v1/orders/" + orderId + "/responses?sort=LOWEST_PRICE", customer)).data().path("items");
        assertThat(api.ids(sorted)).containsExactly(rc, rb, ra);

        assertThat(api.post("/api/v1/responses/" + ra + "/accept", api.customer(), null).status()).isEqualTo(403);
        Result accepted = expectOk(api.post("/api/v1/responses/" + ra + "/accept", customer, null));
        assertThat(accepted.data().path("paymentId").asLong()).isPositive();
        assertThat(accepted.data().path("conversationId").asLong()).isPositive();

        assertThat(status("order_responses", ra)).isEqualTo("ACCEPTED");
        assertThat(status("order_responses", rb)).isEqualTo("DECLINED");
        assertThat(status("order_responses", rc)).isEqualTo("DECLINED");
        Map<String, Object> order = jdbc.queryForMap("SELECT status, accepted_provider_id, accepted_response_id FROM orders WHERE id = ?", orderId);
        assertThat(order.get("status")).isEqualTo("ACCEPTED");
        assertThat(((Number) order.get("accepted_provider_id")).longValue()).isEqualTo(a.providerId());
        assertThat(((Number) order.get("accepted_response_id")).longValue()).isEqualTo(ra);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_policy_snapshots WHERE order_id = ?", Integer.class, orderId)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT amount FROM payments WHERE order_id = ?", BigDecimal.class, orderId)).isEqualByComparingTo("500");

        Result again = api.post("/api/v1/responses/" + rb + "/accept", customer, null);
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.code()).isIn("RESPONSE_ALREADY_ACCEPTED", "ORDER_INVALID_STATE");
        assertThat(api.get("/api/v1/orders/" + orderId, b).status()).isEqualTo(200);
    }

    @Test
    void concurrentAcceptanceLetsExactlyOneOfferWin() throws Exception {
        long s = api.newService();
        Actor a = api.verifiedProvider(List.of(s));
        Actor b = api.verifiedProvider(List.of(s));
        Actor customer = api.customer();
        long orderId = api.order(customer, List.of(s), null);
        long ra = api.offer(a, orderId, new BigDecimal("100"));
        long rb = api.offer(b, orderId, new BigDecimal("120"));

        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            long id = i % 2 == 0 ? ra : rb;
            Callable<Integer> task = () -> {
                start.await();
                return api.post("/api/v1/responses/" + id + "/accept", customer, null).status();
            };
            futures.add(pool.submit(task));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : futures) statuses.add(f.get());
        pool.shutdown();

        assertThat(statuses.stream().filter(x -> x == 200).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(x -> x == 409).count()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_responses WHERE order_id = ? AND status = 'ACCEPTED'", Integer.class, orderId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE order_id = ?", Integer.class, orderId)).isEqualTo(1);
    }

    @Test
    void databaseRejectsASecondAcceptedResponse() {
        long s = api.newService();
        Actor a = api.verifiedProvider(List.of(s));
        Actor b = api.verifiedProvider(List.of(s));
        long orderId = api.order(api.customer(), List.of(s), null);
        long ra = api.offer(a, orderId, BigDecimal.TEN);
        long rb = api.offer(b, orderId, BigDecimal.TEN);
        jdbc.update("UPDATE order_responses SET status = 'ACCEPTED' WHERE id = ?", ra);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("UPDATE order_responses SET status = 'ACCEPTED' WHERE id = ?", rb))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void providersEditAndWithdrawOnlyTheirOwnPendingOffers() {
        long s = api.newService();
        Actor a = api.verifiedProvider(List.of(s));
        Actor b = api.verifiedProvider(List.of(s));
        Actor customer = api.customer();
        long orderId = api.order(customer, List.of(s), null);
        long ra = api.offer(a, orderId, new BigDecimal("200"));
        assertThat(api.patch("/api/v1/responses/" + ra, b, Map.of("laborCost", 1)).status()).isEqualTo(403);
        assertThat(expectOk(api.patch("/api/v1/responses/" + ra, a, Map.of("laborCost", 180))).data().path("price").decimalValue())
                .isEqualByComparingTo("180");
        expectOk(api.post("/api/v1/responses/" + ra + "/withdraw", a, null));
        assertThat(status("order_responses", ra)).isEqualTo("CANCELED");
        assertThat(status("orders", orderId)).isEqualTo("QUOTING");
        assertThat(api.patch("/api/v1/responses/" + ra, a, Map.of("laborCost", 170)).code()).isEqualTo("RESPONSE_NOT_PENDING");
    }

    @Test
    void legacyEndpointsApplyTheNewRules() {
        long s = api.newService();
        Actor p = api.verifiedProvider(List.of(s));
        Actor customer = api.customer();
        Result created = expectStatus(201, api.post("/orders/insert", customer, Map.of("carModelYear", "Kia 2020",
                "date", LocalDate.now().plusDays(1).toString(), "servicesIds", List.of(s), "userId", 1,
                "latitude", TestApi.CAIRO_LAT, "longitude", TestApi.CAIRO_LNG)));
        long orderId = created.data().path("id").asLong();
        assertThat(jdbc.queryForObject("SELECT customer_id FROM orders WHERE id = ?", Long.class, orderId)).isEqualTo(customer.userId());

        Result offer = expectStatus(201, api.post("/responses/insert", p, Map.of("orderId", orderId, "comment", "ok", "laborCost", 250)));
        long responseId = offer.data().path("id").asLong();
        JsonNode list = expectOk(api.get("/responses/get-all-response-of-user", customer)).data();
        assertThat(api.ids(list)).contains(responseId);
        Result accepted = expectOk(api.post("/responses/accept-response", customer, Map.of("responseId", responseId)));
        assertThat(accepted.body().path("success").asBoolean()).isTrue();
        assertThat(status("orders", orderId)).isEqualTo("ACCEPTED");
    }
}
