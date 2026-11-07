package com.doubleclick.wadii;

import com.doubleclick.wadii.support.IntegrationTest;
import com.doubleclick.wadii.support.TestApi.Actor;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.doubleclick.wadii.support.TestApi.money;
import static org.assertj.core.api.Assertions.assertThat;

/** Individual seller listings, notifications and chat (business rules sections 11 and 15). */
class ListingAndChatSpecTest extends IntegrationTest {

    @Test
    void listingNeedsModerationRespectsTheQuotaAndRecordsTheSellerFee() {
        api.config("SELLER", Map.of("maxActiveListings", 2, "feeMode", "FIXED", "feeValue", 25, "feeTiming", "ON_PUBLISH",
                "listingDurationDays", 30, "moderationRequired", true));
        try {
            Actor seller = api.customer();
            long first = listing(seller, "Used battery 70Ah");
            api.post("/api/v1/listings/" + first + "/submit", seller, null).ok();
            assertThat(api.get("/api/v1/listings/" + first, null).status()).as("not public before approval").isEqualTo(404);

            Actor admin = api.admin();
            api.post("/api/v1/admin/listings/" + first + "/approve", admin, null).ok();
            assertThat(api.get("/api/v1/listings/" + first, null).ok().data().path("status").asText()).isEqualTo("ACTIVE");
            var summary = api.get("/api/v1/me/seller", seller).ok().data();
            assertThat(money(summary.path("feesDue"))).isEqualByComparingTo("25.00");

            long second = listing(seller, "Alloy wheels");
            api.post("/api/v1/listings/" + second + "/submit", seller, null).ok();
            long third = listing(seller, "Roof rack");
            assertThat(api.post("/api/v1/listings/" + third + "/submit", seller, null).code()).isEqualTo("LISTING_LIMIT_REACHED");

            api.post("/api/v1/listings/" + first + "/mark-sold", seller, null).ok();
            api.post("/api/v1/listings/" + third + "/submit", seller, null).ok();
            assertThat(api.get("/api/v1/me/seller", seller).ok().data().path("activeListings").asInt()).isEqualTo(2);
            assertThat(api.get("/api/v1/me", seller).ok().data().path("capabilities").has("provider"))
                    .as("selling never makes the account a provider").isFalse();
        } finally {
            api.config("SELLER", Map.of("maxActiveListings", 3, "feeMode", "NONE", "feeValue", 0, "feeTiming", "ON_PUBLISH",
                    "listingDurationDays", 30, "moderationRequired", true));
        }
    }

    @Test
    void buyerAndSellerCanChatAndUnreadCountsAreTracked() {
        Actor seller = api.customer();
        long id = listing(seller, "Car cover");
        api.post("/api/v1/listings/" + id + "/submit", seller, null).ok();
        api.post("/api/v1/admin/listings/" + id + "/approve", api.admin(), null).ok();

        Actor buyer = api.customer();
        long conversation = api.post("/api/v1/conversations", buyer, Map.of("kind", "LISTING", "refId", id)).ok().data().path("id").asLong();
        api.post("/api/v1/conversations/" + conversation + "/messages", buyer, Map.of("body", "Is it still available?")).ok();
        var list = api.get("/api/v1/conversations", seller).ok().data().path("items");
        assertThat(list.get(0).path("unread").asInt()).isEqualTo(1);
        var messages = api.get("/api/v1/conversations/" + conversation + "/messages", seller).ok().data();
        assertThat(messages.get(0).path("body").asText()).isEqualTo("Is it still available?");
        assertThat(api.get("/api/v1/conversations", seller).ok().data().path("items").get(0).path("unread").asInt()).isZero();
        assertThat(api.get("/api/v1/notifications/unread-count", seller).ok().data().path("count").asLong()).isPositive();

        Actor stranger = api.customer();
        assertThat(api.get("/api/v1/conversations/" + conversation + "/messages", stranger).status()).isEqualTo(404);
    }

    private long listing(Actor seller, String title) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("category", "ACCESSORIES");
        body.put("condition", "GOOD");
        body.put("description", "Works well");
        body.put("price", 500);
        body.put("areaId", 1);
        body.put("contactMethod", "CHAT");
        body.put("photoKeys", List.of(api.upload(seller, "LISTING_PHOTO")));
        return api.post("/api/v1/listings", seller, body).ok().data().path("id").asLong();
    }
}
