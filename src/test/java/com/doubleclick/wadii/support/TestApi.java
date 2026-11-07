package com.doubleclick.wadii.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** HTTP-level helpers. Everything goes through MockMvc and the real security filter chain. */
@TestComponent
public class TestApi {

    public static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R', 0, 1, 2, 3};
    public static final long CAIRO = 1L;
    public static final long GIZA = 2L;

    private static final AtomicInteger SEQ = new AtomicInteger((int) (System.nanoTime() % 1_000_000));

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    public record Actor(String token, long userId, Long providerId, String email) {
    }

    public record Result(int status, JsonNode body) {
        public JsonNode data() {
            return body.path("data");
        }

        public String code() {
            return body.path("code").asText(null);
        }

        public Result ok() {
            assertThat(status).as(body.toString()).isBetween(200, 299);
            return this;
        }
    }

    // ---------------------------------------------------------------- raw calls

    public Result get(String url, Actor actor) {
        return exec(MockMvcRequestBuilders.request(HttpMethod.GET, url), actor, null);
    }

    public Result post(String url, Actor actor, Object body) {
        return exec(MockMvcRequestBuilders.request(HttpMethod.POST, url).contentType(MediaType.APPLICATION_JSON), actor, body);
    }

    public Result put(String url, Actor actor, Object body) {
        return exec(MockMvcRequestBuilders.request(HttpMethod.PUT, url).contentType(MediaType.APPLICATION_JSON), actor, body);
    }

    public Result patch(String url, Actor actor, Object body) {
        return exec(MockMvcRequestBuilders.request(HttpMethod.PATCH, url).contentType(MediaType.APPLICATION_JSON), actor, body);
    }

    public Result delete(String url, Actor actor) {
        return exec(MockMvcRequestBuilders.request(HttpMethod.DELETE, url), actor, null);
    }

    public String upload(Actor actor, String purpose) {
        var req = MockMvcRequestBuilders.multipart("/api/v1/files")
                .file(new MockMultipartFile("file", "photo.png", "image/png", PNG)).param("purpose", purpose);
        return exec(req, actor, null).ok().data().path("key").asText();
    }

    public int rawStatus(MockHttpServletRequestBuilder req, Actor actor) {
        try {
            if (actor != null) req.header("Authorization", "Bearer " + actor.token());
            return mvc.perform(req).andReturn().getResponse().getStatus();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Result exec(MockHttpServletRequestBuilder req, Actor actor, Object body) {
        try {
            if (actor != null) req.header("Authorization", "Bearer " + actor.token());
            if (body != null) req.content(json.writeValueAsBytes(body));
            MvcResult r = mvc.perform(req).andReturn();
            String content = r.getResponse().getContentAsString();
            JsonNode node = content.isBlank() || !content.trim().startsWith("{") ? json.createObjectNode() : json.readTree(content);
            return new Result(r.getResponse().getStatus(), node);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---------------------------------------------------------------- identities

    public Actor admin() {
        Result r = post("/api/v1/auth/login", null, Map.of("email", "admin@wadii.test", "password", "AdminPass123!")).ok();
        return new Actor(r.data().path("token").asText(), r.data().path("user").path("id").asLong(), null, "admin@wadii.test");
    }

    public Actor customer() {
        int n = SEQ.incrementAndGet();
        String email = "user" + n + "@wadii.test";
        String phone = String.format("010%08d", n % 100_000_000);
        Result r = post("/api/v1/auth/register", null, Map.of("fullName", "User " + n, "email", email, "phone", phone,
                "password", "Password123")).ok();
        return new Actor(r.data().path("token").asText(), r.data().path("user").path("id").asLong(), null, email);
    }

    /** A verified provider with one branch in Cairo (no coordinates, area matching) offering the given category codes. */
    public Actor provider(String... categoryCodes) {
        Actor user = customer();
        List<Long> ids = new ArrayList<>();
        for (String code : categoryCodes) ids.add(categoryId(code));
        put("/api/v1/provider/application", user, Map.of("businessName", "Garage " + user.userId(), "phone", "0223456789",
                "areaId", CAIRO, "address", "Nasr City", "categoryIds", ids)).ok();
        String key = upload(user, "PROVIDER_DOCUMENT");
        post("/api/v1/provider/application/documents", user, Map.of("type", "COMMERCIAL_REGISTER", "fileKey", key)).ok();
        long providerId = post("/api/v1/provider/application/submit", user, null).ok().data().path("providerId").asLong();
        post("/api/v1/admin/provider-applications/" + providerId + "/approve", admin(), null).ok();
        Actor provider = new Actor(user.token(), user.userId(), providerId, user.email());
        post("/api/v1/provider/branches", provider, branch(4)).ok();
        return provider;
    }

    public Map<String, Object> branch(int maxBookings) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("name", "Main branch");
        b.put("areaId", CAIRO);
        b.put("address", "Abbas El Akkad");
        b.put("phone", "0223456789");
        b.put("slotMinutes", 60);
        b.put("maxBookingsPerSlot", maxBookings);
        b.put("active", true);
        return b;
    }

    public long branchId(Actor provider) {
        return get("/api/v1/provider/branches", provider).ok().data().get(0).path("id").asLong();
    }

    public long categoryId(String code) {
        for (JsonNode c : get("/api/v1/catalog/categories", null).ok().data()) {
            if (c.path("code").asText().equals(code)) return c.path("id").asLong();
        }
        throw new IllegalArgumentException(code);
    }

    // ---------------------------------------------------------------- marketplace helpers

    /** Creates a request with a vehicle in Cairo for the given category codes; returns the request detail. */
    public JsonNode request(Actor customer, String... categoryCodes) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (String code : categoryCodes) items.add(Map.of("categoryId", categoryId(code), "description", "Need " + code));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vehicle", Map.of("make", "Chevrolet", "model", "Lanos", "year", 2016, "save", true));
        body.put("items", items);
        body.put("areaId", CAIRO);
        body.put("address", "Nasr City");
        return post("/api/v1/requests", customer, body).ok().data();
    }

    public long itemId(JsonNode request, String code) {
        for (JsonNode i : request.path("items")) {
            if (i.path("category").path("code").asText().equals(code)) return i.path("id").asLong();
        }
        throw new IllegalArgumentException(code);
    }

    public Map<String, Object> line(long itemId, int price, boolean warranty) {
        Map<String, Object> l = new LinkedHashMap<>();
        l.put("serviceItemId", itemId);
        l.put("available", true);
        l.put("servicePrice", price);
        l.put("laborCost", 50);
        l.put("discount", 0);
        l.put("parts", List.of(Map.of("name", "Part", "quantity", 2, "unitPrice", 25)));
        l.put("estimatedMinutes", 45);
        l.put("warranty", warranty ? Map.of("provided", true, "durationDays", 90, "coverage", "PARTS_AND_LABOR")
                : Map.of("provided", false));
        return l;
    }

    public JsonNode offer(Actor provider, long requestId, List<Map<String, Object>> lines) {
        return post("/api/v1/provider/requests/" + requestId + "/offer", provider,
                Map.of("branchId", branchId(provider), "lines", lines)).ok().data();
    }

    public long lineId(JsonNode offer, long itemId) {
        for (JsonNode l : offer.path("lines")) {
            if (l.path("serviceItemId").asLong() == itemId) return l.path("lineId").asLong();
        }
        throw new IllegalArgumentException("item " + itemId);
    }

    public JsonNode accept(Actor customer, long requestId, long itemId, long lineId) {
        return post("/api/v1/requests/" + requestId + "/items/" + itemId + "/accept", customer, Map.of("lineId", lineId)).ok().data();
    }

    public JsonNode accept(Actor customer, long requestId, long itemId, long lineId, Instant appointmentStart) {
        return post("/api/v1/requests/" + requestId + "/items/" + itemId + "/accept", customer,
                Map.of("lineId", lineId, "appointmentStart", appointmentStart.toString())).ok().data();
    }

    public void config(String key, Map<String, Object> payload) {
        post("/api/v1/admin/config/" + key, admin(), Map.of("payload", payload, "reason", "test")).ok();
    }

    public static BigDecimal money(JsonNode node) {
        return node.decimalValue().setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
