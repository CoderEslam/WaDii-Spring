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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** HTTP-level helpers. Everything goes through MockMvc and the real security filter chain. */
@TestComponent
public class TestApi {

    public static final BigDecimal CAIRO_LAT = new BigDecimal("30.044420");
    public static final BigDecimal CAIRO_LNG = new BigDecimal("31.235712");
    public static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R', 0, 1, 2, 3};

    private static final AtomicInteger SEQ = new AtomicInteger();

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
    }

    // ---------------------------------------------------------------- raw calls

    public Result call(HttpMethod method, String url, Actor actor, Object body) {
        MockHttpServletRequestBuilder req = MockMvcRequestBuilders.request(method, url).contentType(MediaType.APPLICATION_JSON);
        return exec(req, actor, body);
    }

    public Result get(String url, Actor actor) {
        return call(HttpMethod.GET, url, actor, null);
    }

    public Result post(String url, Actor actor, Object body) {
        return call(HttpMethod.POST, url, actor, body);
    }

    public Result put(String url, Actor actor, Object body) {
        return call(HttpMethod.PUT, url, actor, body);
    }

    public Result patch(String url, Actor actor, Object body) {
        return call(HttpMethod.PATCH, url, actor, body);
    }

    public Result multipart(String url, Actor actor, String filename, String contentType, byte[] content, Map<String, String> params) {
        MockMultipartHttpServletRequestBuilder req = MockMvcRequestBuilders.multipart(url)
                .file(new MockMultipartFile("file", filename, contentType, content));
        params.forEach(req::param);
        return exec(req, actor, null);
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
            JsonNode node = content.isBlank() ? json.createObjectNode() : json.readTree(content);
            return new Result(r.getResponse().getStatus(), node);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---------------------------------------------------------------- identities

    public Actor admin() {
        Result r = post("/api/v1/auth/login", null, Map.of("email", "admin@wadii.test", "password", "AdminPass123!"));
        return new Actor(r.data().path("token").asText(), r.data().path("user").path("id").asLong(), null, "admin@wadii.test");
    }

    public long anyCityId() {
        Long countryId = get("/api/v1/catalog/countries", null).data().get(0).path("id").asLong();
        Long provinceId = get("/api/v1/catalog/countries/" + countryId + "/provinces", null).data().get(0).path("id").asLong();
        return get("/api/v1/catalog/provinces/" + provinceId + "/cities", null).data().get(0).path("id").asLong();
    }

    public Result register(String email, int userType, String providerName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstName", "Test");
        body.put("lastName", "User");
        body.put("email", email);
        body.put("password", "Password123");
        body.put("phone", "+201000000000");
        body.put("cityId", anyCityId());
        body.put("userType", userType);
        if (providerName != null) body.put("providerName", providerName);
        return post("/api/v1/auth/register", null, body);
    }

    public static String uniqueEmail(String prefix) {
        return prefix + "-" + SEQ.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.wadii";
    }

    public Actor customer() {
        String email = uniqueEmail("customer");
        Result r = register(email, 0, null);
        if (r.status() != 201) throw new IllegalStateException("register failed: " + r.body());
        return new Actor(r.data().path("token").asText(), r.data().path("user").path("id").asLong(), null, email);
    }

    /** Registered provider, NOT verified yet. */
    public Actor pendingProvider(String name) {
        String email = uniqueEmail("provider");
        Result r = register(email, 1, name);
        if (r.status() != 201) throw new IllegalStateException("register failed: " + r.body());
        return new Actor(r.data().path("token").asText(), r.data().path("user").path("id").asLong(), r.data().path("providerId").asLong(), email);
    }

    /** Verified provider offering {@code serviceIds}, one branch at the given location (no published hours), receiving accounts set. */
    public Actor verifiedProvider(List<Long> serviceIds, BigDecimal lat, BigDecimal lng) {
        Actor p = pendingProvider("Provider " + SEQ.incrementAndGet());
        expectOk(put("/api/v1/providers/me/services", p, Map.of("serviceIds", serviceIds)));
        Map<String, Object> branch = new LinkedHashMap<>();
        branch.put("name", "Main");
        branch.put("address", "Cairo");
        branch.put("latitude", lat);
        branch.put("longitude", lng);
        expectStatus(201, post("/api/v1/providers/me/branches", p, branch));
        expectOk(put("/api/v1/providers/me/payment-accounts/VODAFONE_CASH", p, Map.of("accountName", "Shop", "accountIdentifier", "01011111111")));
        expectOk(put("/api/v1/providers/me/payment-accounts/INSTAPAY", p, Map.of("accountName", "Shop", "accountIdentifier", "shop@instapay")));
        expectOk(post("/api/v1/admin/providers/" + p.providerId() + "/verification", admin(), Map.of("decision", "VERIFIED")));
        return p;
    }

    public Actor verifiedProvider(List<Long> serviceIds) {
        return verifiedProvider(serviceIds, CAIRO_LAT, CAIRO_LNG);
    }

    /** A fresh catalog service so each test controls exactly which providers match. */
    public long newService() {
        Result r = post("/api/v1/catalog/services", admin(), Map.of("name", "svc-" + UUID.randomUUID()));
        expectStatus(201, r);
        return r.data().path("id").asLong();
    }

    // ---------------------------------------------------------------- orders / offers

    public Result createOrder(Actor customer, List<Long> serviceIds, Long providerId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("carDescription", "Toyota Corolla 2018");
        body.put("requestedDate", LocalDate.now().plusDays(2).toString());
        body.put("latitude", CAIRO_LAT);
        body.put("longitude", CAIRO_LNG);
        body.put("serviceIds", serviceIds);
        body.put("spareParts", List.of("Oil filter"));
        if (providerId != null) body.put("providerId", providerId);
        return post("/api/v1/orders", customer, body);
    }

    public long order(Actor customer, List<Long> serviceIds, Long providerId) {
        Result r = createOrder(customer, serviceIds, providerId);
        expectStatus(201, r);
        return r.data().path("id").asLong();
    }

    public Result submitOffer(Actor provider, long orderId, BigDecimal labor, String expiresAt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", orderId);
        body.put("description", "Full service");
        body.put("laborCost", labor);
        body.put("estimatedDurationMinutes", 90);
        if (expiresAt != null) body.put("expiresAt", expiresAt);
        return post("/api/v1/responses", provider, body);
    }

    public long offer(Actor provider, long orderId, BigDecimal labor) {
        Result r = submitOffer(provider, orderId, labor, null);
        expectStatus(201, r);
        return r.data().path("id").asLong();
    }

    /** Order with one accepted offer; returns {orderId, responseId, paymentId}. */
    public long[] acceptedOrder(Actor customer, Actor provider, List<Long> services, BigDecimal price) {
        long orderId = order(customer, services, provider.providerId());
        long responseId = offer(provider, orderId, price);
        Result accepted = post("/api/v1/responses/" + responseId + "/accept", customer, null);
        expectOk(accepted);
        return new long[]{orderId, responseId, accepted.data().path("paymentId").asLong()};
    }

    public List<Long> ids(JsonNode items) {
        List<Long> ids = new ArrayList<>();
        items.forEach(n -> ids.add(n.path("id").asLong()));
        return ids;
    }

    // ---------------------------------------------------------------- assertions

    public static Result expectOk(Result r) {
        return expectStatus(200, r);
    }

    public static Result expectStatus(int status, Result r) {
        if (r.status() != status) {
            throw new AssertionError("Expected HTTP " + status + " but got " + r.status() + ": " + r.body());
        }
        return r;
    }
}
