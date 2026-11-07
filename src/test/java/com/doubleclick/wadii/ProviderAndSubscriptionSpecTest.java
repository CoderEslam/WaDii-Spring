package com.doubleclick.wadii;

import com.doubleclick.wadii.subscription.SubscriptionService;
import com.doubleclick.wadii.support.IntegrationTest;
import com.doubleclick.wadii.support.TestApi.Actor;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Provider application, modes and subscription (business rules sections 3 and 7.4). */
class ProviderAndSubscriptionSpecTest extends IntegrationTest {

    @Autowired
    SubscriptionService subscriptions;

    @Test
    void applicationNeedsReviewAndProviderModeKeepsCustomerMode() {
        Actor user = api.customer();
        api.put("/api/v1/provider/application", user, Map.of("businessName", "Speed Garage", "phone", "0223456789",
                "areaId", 1, "address", "Downtown", "categoryIds", List.of(api.categoryId("TIRES")))).ok();
        assertThat(api.post("/api/v1/provider/application/submit", user, null).code()).isEqualTo("APPLICATION_INCOMPLETE");
        String key = api.upload(user, "PROVIDER_DOCUMENT");
        api.post("/api/v1/provider/application/documents", user, Map.of("type", "NATIONAL_ID", "fileKey", key)).ok();
        long providerId = api.post("/api/v1/provider/application/submit", user, null).ok().data().path("providerId").asLong();

        Actor stranger = api.customer();
        assertThat(api.rawStatus(MockMvcRequestBuilders.get("/api/v1/files/" + key), stranger)).as("documents are private").isEqualTo(403);
        assertThat(api.get("/api/v1/provider/branches", user).code()).isEqualTo("PROVIDER_NOT_ACTIVE");
        assertThat(api.post("/api/v1/admin/provider-applications/" + providerId + "/approve", stranger, null).status()).isEqualTo(403);

        api.post("/api/v1/admin/provider-applications/" + providerId + "/reject", api.admin(), Map.of("reason", "Blurry ID")).ok();
        assertThat(api.get("/api/v1/provider/application", user).ok().data().path("rejectionReason").asText()).isEqualTo("Blurry ID");
        api.post("/api/v1/provider/application/submit", user, null).ok();
        api.post("/api/v1/admin/provider-applications/" + providerId + "/approve", api.admin(), null).ok();

        var caps = api.get("/api/v1/me", user).ok().data().path("capabilities");
        assertThat(caps.path("customer").asBoolean()).isTrue();
        assertThat(caps.path("provider").path("canOperate").asBoolean()).isTrue();
        api.get("/api/v1/vehicles", user).ok();
    }

    @Test
    void suspensionStopsProviderActivityButNotTheirCustomerMode() {
        Actor p = api.provider("BATTERIES");
        Actor customer = api.customer();
        JsonNode request = api.request(customer, "BATTERIES");
        long item = api.itemId(request, "BATTERIES");
        api.offer(p, request.path("id").asLong(), List.of(api.line(item, 900, false)));

        api.post("/api/v1/admin/providers/" + p.providerId() + "/suspend", api.admin(), Map.of("reason", "Complaints")).ok();
        assertThat(api.get("/api/v1/provider/requests", p).code()).isEqualTo("PROVIDER_NOT_ACTIVE");
        assertThat(api.get("/api/v1/provider/offers", api.admin()).status()).isEqualTo(403);
        var detail = api.get("/api/v1/requests/" + request.path("id").asLong(), customer).ok().data();
        assertThat(detail.path("items").get(0).path("offers").get(0).path("status").asText()).isEqualTo("WITHDRAWN");
        api.request(p, "TIRES");
    }

    @Test
    void unpaidSubscriptionRestrictsNewActivityAfterGraceAndPaymentRenewsFromPaymentDate() {
        Actor p = api.provider("OIL_CHANGE");
        api.config("SUBSCRIPTION", Map.of("monthlyPrice", 300, "graceDays", 7, "reminderDaysBefore", List.of(7, 3)));
        try {
            subscriptions.runDaily();
            var sub = api.get("/api/v1/provider/subscription", p).ok().data();
            assertThat(sub.path("status").asText()).isEqualTo("PAYMENT_OVERDUE");
            assertThat(api.get("/api/v1/provider/ledger?type=SUBSCRIPTION", p).ok().data().path("totalItems").asInt()).isEqualTo(1);

            clock.advance(Duration.ofDays(8));
            assertThat(api.get("/api/v1/provider/subscription", p).ok().data().path("status").asText()).isEqualTo("RESTRICTED");
            Actor customer = api.customer();
            JsonNode request = api.request(customer, "OIL_CHANGE");
            assertThat(api.post("/api/v1/provider/requests/" + request.path("id").asLong() + "/offer", p,
                    Map.of("branchId", api.branchId(p), "lines", List.of(api.line(api.itemId(request, "OIL_CHANGE"), 200, false))))
                    .code()).isIn("SUBSCRIPTION_RESTRICTED", "NOT_TARGETED");
            api.get("/api/v1/vehicles", p).ok();
            clock.reset();

            LocalDate today = LocalDate.now(com.doubleclick.wadii.common.util.Times.CAIRO);
            api.post("/api/v1/admin/subscriptions/" + p.providerId() + "/payments", api.admin(),
                    Map.of("amount", 300, "paidOn", today.toString(), "method", "CASH")).ok();
            var renewed = api.get("/api/v1/provider/subscription", p).ok().data();
            assertThat(renewed.path("nextDueDate").asText()).isEqualTo(today.plusMonths(1).toString());
            assertThat(renewed.path("status").asText()).isEqualTo("ACTIVE");

            api.post("/api/v1/admin/subscriptions/" + p.providerId() + "/exemptions", api.admin(),
                    Map.of("months", 3, "reason", "Launch partner")).ok();
            assertThat(api.get("/api/v1/provider/subscription", p).ok().data().path("status").asText()).isEqualTo("EXEMPT");
        } finally {
            clock.reset();
            api.config("SUBSCRIPTION", Map.of("monthlyPrice", 0, "graceDays", 7, "reminderDaysBefore", List.of(7, 3)));
        }
    }

    @Test
    void financialConfigurationNeedsTheFinanceRole() {
        Actor admin = api.admin();
        var support = api.post("/api/v1/admin/admins", admin, Map.of("fullName", "Support Sam", "email", "support" + System.nanoTime() + "@wadii.test",
                "phone", String.format("011%08d", System.nanoTime() % 100_000_000), "password", "SupportPass123!",
                "roles", List.of("SUPPORT_ADMIN"))).ok().data();
        var login = api.post("/api/v1/auth/login", null, Map.of("email", support.path("email").asText(), "password", "SupportPass123!")).ok();
        var staff = api.get("/api/v1/admin/users?admin=true&size=100", admin).ok().data().path("items");
        assertThat(staff).isNotEmpty();
        staff.forEach(u -> assertThat(u.path("admin").asBoolean()).isTrue());
        Actor supportAdmin = new Actor(login.data().path("token").asText(), support.path("id").asLong(), null, support.path("email").asText());
        var denied = api.post("/api/v1/admin/config/COMMISSION", supportAdmin,
                Map.of("payload", Map.of("mode", "PERCENT", "value", 50, "payer", "PROVIDER"), "reason", "test"));
        assertThat(denied.status()).isEqualTo(403);
        var invalid = api.post("/api/v1/admin/config/COMMISSION", admin,
                Map.of("payload", Map.of("mode", "PERCENT", "value", 150, "payer", "PROVIDER"), "reason", "test"));
        assertThat(invalid.code()).isEqualTo("CONFIG_INVALID");
        assertThat(api.get("/api/v1/admin/config/COMMISSION", supportAdmin).ok().data().path("history").size()).isPositive();
    }
}
