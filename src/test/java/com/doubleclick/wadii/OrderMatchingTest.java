package com.doubleclick.wadii;

import com.doubleclick.wadii.order.OrderJobs;
import com.doubleclick.wadii.support.IntegrationTest;
import com.doubleclick.wadii.support.TestApi;
import com.doubleclick.wadii.support.TestApi.Actor;
import com.doubleclick.wadii.support.TestApi.Result;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.doubleclick.wadii.support.TestApi.expectOk;
import static org.assertj.core.api.Assertions.assertThat;

class OrderMatchingTest extends IntegrationTest {

    @Autowired
    TestApi api;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    OrderJobs jobs;

    private List<Long> targetedProviders(long orderId) {
        return jdbc.queryForList("SELECT provider_id FROM order_provider_targets WHERE order_id = ? ORDER BY provider_id", Long.class, orderId);
    }

    @Test
    void automaticMatchingRequiresAllRequestedServices() {
        long s1 = api.newService(), s2 = api.newService();
        Actor a = api.verifiedProvider(List.of(s1, s2));
        Actor b = api.verifiedProvider(List.of(s1));
        Actor c = api.verifiedProvider(List.of(s2));
        Actor customer = api.customer();

        Result created = api.createOrder(customer, List.of(s1, s2), null);
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.data().path("status").asText()).isEqualTo("QUOTING");
        long orderId = created.data().path("id").asLong();

        assertThat(targetedProviders(orderId)).containsExactly(a.providerId());
        assertThat(targetedProviders(orderId)).doesNotContain(b.providerId(), c.providerId());
        Integer notified = jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = 'ORDER_TARGETED' AND order_id = ?",
                Integer.class, a.userId(), orderId);
        assertThat(notified).isEqualTo(1);
    }

    @Test
    void explicitProviderReceivesTheOrderAlone() {
        long s1 = api.newService();
        Actor chosen = api.verifiedProvider(List.of(s1));
        Actor other = api.verifiedProvider(List.of(s1));
        long orderId = api.order(api.customer(), List.of(s1), chosen.providerId());
        assertThat(targetedProviders(orderId)).containsExactly(chosen.providerId());
        Integer otherNotified = jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND order_id = ?",
                Integer.class, other.userId(), orderId);
        assertThat(otherNotified).isZero();
    }

    @Test
    void explicitProviderIsValidated() {
        long s1 = api.newService(), s2 = api.newService();
        Actor onlyS1 = api.verifiedProvider(List.of(s1));
        Actor customer = api.customer();
        Result mismatch = api.createOrder(customer, List.of(s1, s2), onlyS1.providerId());
        assertThat(mismatch.status()).isEqualTo(422);
        assertThat(mismatch.code()).isEqualTo("PROVIDER_SERVICES_MISMATCH");

        Actor pending = api.pendingProvider("Unverified");
        expectOk(api.put("/api/v1/providers/me/services", pending, Map.of("serviceIds", List.of(s1))));
        Result notEligible = api.createOrder(customer, List.of(s1), pending.providerId());
        assertThat(notEligible.status()).isEqualTo(422);
        assertThat(notEligible.code()).isEqualTo("PROVIDER_NOT_ELIGIBLE");

        Result self = api.createOrder(onlyS1, List.of(s1), onlyS1.providerId());
        assertThat(self.code()).isEqualTo("SELF_ORDER_FORBIDDEN");
    }

    @Test
    void providersOutsideTheirRadiusAreNotMatched() {
        long s1 = api.newService();
        Actor near = api.verifiedProvider(List.of(s1));
        Actor alexandria = api.verifiedProvider(List.of(s1), new BigDecimal("31.200092"), new BigDecimal("29.918739"));
        long orderId = api.order(api.customer(), List.of(s1), null);
        assertThat(targetedProviders(orderId)).containsExactly(near.providerId()).doesNotContain(alexandria.providerId());
    }

    @Test
    void unverifiedAndSuspendedProvidersAreNotMatched() {
        long s1 = api.newService();
        Actor pending = api.pendingProvider("Pending");
        expectOk(api.put("/api/v1/providers/me/services", pending, Map.of("serviceIds", List.of(s1))));
        Actor suspended = api.verifiedProvider(List.of(s1));
        expectOk(api.post("/api/v1/admin/providers/" + suspended.providerId() + "/status", api.admin(), Map.of("status", "SUSPENDED", "reason", "test")));
        Actor active = api.verifiedProvider(List.of(s1));
        long orderId = api.order(api.customer(), List.of(s1), null);
        assertThat(targetedProviders(orderId)).containsExactly(active.providerId());
    }

    @Test
    void ordersWithoutProvidersWaitInMatchingAndAreRematched() {
        long s1 = api.newService();
        Result created = api.createOrder(api.customer(), List.of(s1), null);
        assertThat(created.data().path("status").asText()).isEqualTo("MATCHING");
        long orderId = created.data().path("id").asLong();
        Actor late = api.verifiedProvider(List.of(s1));
        jobs.runRematch();
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId)).isEqualTo("QUOTING");
        assertThat(targetedProviders(orderId)).containsExactly(late.providerId());
    }

    @Test
    void orderVisibilityFollowsOwnershipAndTargeting() {
        long s1 = api.newService();
        Actor provider = api.verifiedProvider(List.of(s1));
        Actor stranger = api.verifiedProvider(List.of(api.newService()));
        Actor owner = api.customer();
        long orderId = api.order(owner, List.of(s1), null);

        assertThat(api.get("/api/v1/orders/" + orderId, owner).data().path("customer").path("userId").asLong()).isEqualTo(owner.userId());
        assertThat(api.get("/api/v1/orders/" + orderId, api.customer()).status()).isEqualTo(403);
        assertThat(api.get("/api/v1/orders/" + orderId, stranger).status()).isEqualTo(403);

        JsonNode providerView = expectOk(api.get("/api/v1/orders/" + orderId, provider)).data();
        assertThat(providerView.path("customer").isMissingNode()).isTrue();
        assertThat(providerView.path("myTargetStatus").asText()).isEqualTo("VIEWED");
        assertThat(api.get("/api/v1/orders/" + orderId + "/responses", provider).status()).isEqualTo(403);
        assertThat(api.get("/api/v1/orders/" + orderId + "/timeline", provider).status()).isEqualTo(403);

        JsonNode mine = expectOk(api.get("/api/v1/orders/mine", owner)).data();
        assertThat(api.ids(mine.path("items"))).contains(orderId);
        assertThat(mine.path("size").asInt()).isEqualTo(20);
    }

    @Test
    void customerCanCancelBeforeAcceptanceAndHistoryIsKept() {
        long s1 = api.newService();
        Actor provider = api.verifiedProvider(List.of(s1));
        Actor owner = api.customer();
        long orderId = api.order(owner, List.of(s1), null);
        long responseId = api.offer(provider, orderId, new BigDecimal("300.00"));
        assertThat(api.post("/api/v1/orders/" + orderId + "/cancel", api.customer(), Map.of("reason", "x")).status()).isEqualTo(403);
        Result canceled = expectOk(api.post("/api/v1/orders/" + orderId + "/cancel", owner, Map.of("reason", "changed my mind")));
        assertThat(canceled.data().path("order").path("status").asText()).isEqualTo("CANCELED");
        assertThat(jdbc.queryForObject("SELECT status FROM order_responses WHERE id = ?", String.class, responseId)).isEqualTo("CANCELED");
        assertThat(jdbc.queryForObject("SELECT actor_role FROM order_cancellations WHERE order_id = ?", String.class, orderId)).isEqualTo("CUSTOMER");
        assertThat(api.post("/api/v1/orders/" + orderId + "/cancel", owner, null).code()).isEqualTo("ORDER_INVALID_STATE");
    }
}
