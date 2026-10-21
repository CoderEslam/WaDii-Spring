package com.doubleclick.wadii.flow;

import com.doubleclick.wadii.entities.City;
import com.doubleclick.wadii.entities.Country;
import com.doubleclick.wadii.entities.Province;
import com.doubleclick.wadii.repository.CityRepository;
import com.doubleclick.wadii.repository.CountryRepository;
import com.doubleclick.wadii.repository.ProvinceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CountDownLatch;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the secured order flow end to end against an in-memory database:
 * registration rules, ownership, provider targeting, one accepted response,
 * chat/call access, completion and rating.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:wadii;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=DAY",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=WARN",
        "security.jwt.secret-key=test-secret-key-test-secret-key-test-secret-key-1234",
        "agora.app-id=test", "agora.app-certificate=test",
        "livekit.api-key=test", "livekit.api-secret=test-secret-test-secret-test-secret-12", "livekit.url=wss://example.test",
        "app.admin.email=admin@wadii.test", "app.admin.password=AdminPass1"
})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderFlowIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired CountryRepository countryRepository;
    @Autowired ProvinceRepository provinceRepository;
    @Autowired CityRepository cityRepository;

    Long cityId;
    String adminToken, customerToken, otherCustomerToken, provider1Token, provider2Token, provider3Token;
    long customerId, provider1UserId, provider2UserId;
    long service1, service2, reasonId;
    long provider1Id, provider2Id, provider3Id;
    long orderId, sparePartId, response1Id;

    @BeforeAll
    void seed() throws Exception {
        Country country = new Country();
        country.setName("Egypt");
        country = countryRepository.save(country);
        Province province = new Province();
        province.setName("Cairo");
        province.setCountry(country);
        province = provinceRepository.save(province);
        City city = new City();
        city.setName("Nasr City");
        city.setProvince(province);
        cityId = cityRepository.save(city).getId();

        adminToken = login("admin@wadii.test", "AdminPass1");
        service1 = id(call(post("/services/insert", Map.of("name", "Oil change")), adminToken, 200).get("data"));
        service2 = id(call(post("/services/insert", Map.of("name", "Brakes")), adminToken, 200).get("data"));
        reasonId = id(call(post("/reasons/insert", Map.of("reason", "Changed my mind")), adminToken, 200).get("data"));

        customerToken = register("customer@wadii.test", 0, null).get("token").asText();
        customerId = call(get("/users/me"), customerToken, 200).get("data").get("id").asLong();
        otherCustomerToken = register("other@wadii.test", 0, null).get("token").asText();

        JsonNode p1 = register("p1@wadii.test", 1, "Fast Motors");
        provider1Token = p1.get("token").asText();
        provider1UserId = p1.get("id").asLong();
        JsonNode p2 = register("p2@wadii.test", 1, "Half Garage");
        provider2Token = p2.get("token").asText();
        provider2UserId = p2.get("id").asLong();
        provider3Token = register("p3@wadii.test", 1, "Third Garage").get("token").asText();

        provider1Id = providerId(provider1Token);
        provider2Id = providerId(provider2Token);
        provider3Id = providerId(provider3Token);
        // p1 offers both services, p2 and p3 only the first one
        setServices(provider1Token, provider1Id, service1, service2);
        setServices(provider2Token, provider2Id, service1);
        setServices(provider3Token, provider3Id, service1);
    }

    // ------------------------------------------------------------------ security basics

    @Test
    @Order(1)
    void publicRegistrationCannotCreateAnAdmin() throws Exception {
        call(post("/auth/register", registration("evil@wadii.test", 2, null)), null, 403);
        call(post("/auth/register", registration("bad@wadii.test", 7, null)), null, 400);
    }

    @Test
    @Order(2)
    void userJsonNeverContainsSecrets() throws Exception {
        JsonNode me = call(get("/users/me"), customerToken, 200).get("data");
        assertFalse(me.has("password"));
        assertFalse(me.has("token"));
        assertFalse(me.has("fcmToken"));
        // login still returns the token for the client
        JsonNode login = call(post("/auth/login", Map.of("email", "customer@wadii.test", "password", "Secret123", "fcmToken", "")), null, 200).get("data");
        assertTrue(login.hasNonNull("token"));
        assertFalse(login.has("password"));
        call(post("/auth/login", Map.of("email", "customer@wadii.test", "password", "wrong", "fcmToken", "")), null, 401);
    }

    @Test
    @Order(3)
    void adminOnlyEndpointsRejectOtherRoles() throws Exception {
        call(get("/users/show-all"), customerToken, 403);
        call(get("/users/show-all"), adminToken, 200);
        call(get("/provider-requests/show-all"), customerToken, 403);
        call(post("/provider-requests/put-it-user/" + provider1UserId, Map.of()), customerToken, 403);
        call(post("/services/insert", Map.of("name", "Hack")), customerToken, 403);
        call(get("/orders/show-all"), customerToken, 403);
        call(get("/users/show-all"), null, 401);
    }

    @Test
    @Order(4)
    void usersCannotModifyOtherUsers() throws Exception {
        call(post("/users/update", Map.of("id", provider1UserId, "firstName", "Hacked", "cityId", cityId)), customerToken, 403);
        call(delete("/users/delete/" + provider1UserId), customerToken, 403);
        call(get("/users/" + provider1UserId + "/online"), customerToken, 200); // allowed, but always false without a connection
    }

    // ------------------------------------------------------------------ order flow

    @Test
    @Order(10)
    void orderOwnerComesFromTheTokenAndMatchingTargetsOnlyProvidersWithAllServices() throws Exception {
        JsonNode order = call(post("/orders/insert", Map.of(
                "userId", 999999, // must be ignored
                "carModelYear", "Camry 2018", "comment", "brakes and oil", "date", tomorrow(),
                "latitude", 30.0, "longitude", 31.0,
                "servicesIds", List.of(service1, service2),
                "spareParts", List.of(Map.of("sparePartName", "Brake pad")))), customerToken, 200).get("data");
        orderId = order.get("id").asLong();
        assertEquals("PENDING", order.get("status").asText());
        assertEquals(customerId, order.get("user").get("id").asLong());
        sparePartId = order.get("spareParts").get(0).get("id").asLong();

        // only provider 1 offers BOTH services
        assertEquals(1, call(get("/orders/show-all-order-of-provider"), provider1Token, 200).get("data").size());
        assertEquals(0, call(get("/orders/show-all-order-of-provider"), provider2Token, 200).get("data").size());
        call(get("/orders/show/" + orderId), provider2Token, 403);
        call(get("/orders/show/" + orderId), otherCustomerToken, 403);
        call(get("/orders/show/" + orderId), provider1Token, 200);
        call(get("/orders/show/" + orderId), customerToken, 200);
        // the provider got an inbox notification without customer details
        JsonNode inbox = call(get("/notifications/my"), provider1Token, 200).get("data");
        assertEquals("NEW_ORDER", inbox.get(0).get("type").asText());
        assertEquals(orderId, inbox.get(0).get("orderId").asLong());
    }

    @Test
    @Order(11)
    void orderValidationAndOwnership() throws Exception {
        call(post("/orders/insert", Map.of("carModelYear", "x", "comment", "c", "date", tomorrow(), "servicesIds", List.of())), customerToken, 400);
        call(post("/orders/insert", Map.of("carModelYear", "x", "comment", "c", "date", tomorrow(), "servicesIds", List.of(424242))), customerToken, 400);
        call(post("/orders/insert", Map.of("carModelYear", "x", "comment", "c", "date", "2000-01-01", "servicesIds", List.of(service1))), customerToken, 422);
        // provider 2 does not offer service 2, so a direct order to them is refused
        call(post("/orders/insert", Map.of("carModelYear", "x", "comment", "c", "date", tomorrow(),
                "servicesIds", List.of(service2), "providerId", provider2Id)), customerToken, 422);
        call(post("/orders/update", Map.of("id", orderId, "comment", "hijack")), otherCustomerToken, 403);
        call(delete("/orders/delete/" + orderId), otherCustomerToken, 403);
    }

    @Test
    @Order(12)
    void providerRespondsOnlyToInvitedOrdersAndOnlyOnce() throws Exception {
        Map<String, Object> body = Map.of("orderId", orderId, "providerId", provider2Id, "comment", "I can do it tomorrow",
                "sparePartsPrice", List.of(Map.of("id", sparePartId, "price", 120.50)));
        call(post("/responses/insert", body), provider2Token, 403); // not invited
        call(post("/responses/insert", body), customerToken, 403); // not a provider

        JsonNode created = call(post("/responses/insert", Map.of("orderId", orderId, "providerId", provider2Id, "comment", "ok",
                "sparePartsPrice", List.of(Map.of("id", sparePartId, "price", 120.50)))), provider1Token, 200).get("data");
        response1Id = created.get("id").asLong();
        assertEquals("PENDING", created.get("responsesState").asText());
        assertEquals(provider1Id, created.get("provider").get("id").asLong()); // providerId in the body was ignored
        assertEquals(0, new java.math.BigDecimal("120.50").compareTo(created.get("totalPrice").decimalValue()));

        call(post("/responses/insert", body), provider1Token, 409); // duplicate
        call(post("/responses/insert", Map.of("orderId", orderId, "comment", "bad",
                "sparePartsPrice", List.of(Map.of("id", 987654, "price", 1)))), provider1Token, 409);
        assertEquals("RESPONSES_AVAILABLE", call(get("/orders/show/" + orderId), customerToken, 200).get("data").get("status").asText());
    }

    @Test
    @Order(13)
    void onlyTheOrderOwnerCanAcceptAndOnlyOneResponseWins() throws Exception {
        call(post("/responses/accept-response", Map.of("responseId", response1Id)), otherCustomerToken, 403);
        call(post("/responses/accept-response", Map.of("responseId", response1Id)), provider1Token, 403);

        JsonNode accepted = call(post("/responses/accept-response", Map.of("responseId", response1Id)), customerToken, 200).get("data");
        assertEquals("ACCEPTED", accepted.get("responsesState").asText());
        JsonNode order = call(get("/orders/show/" + orderId), customerToken, 200).get("data");
        assertEquals("ACCEPTED", order.get("status").asText());
        assertEquals(provider1Id, order.get("acceptedProviderId").asLong());

        call(post("/responses/accept-response", Map.of("responseId", response1Id)), customerToken, 409);
        call(post("/orders/cancel", Map.of("orderId", orderId, "reasonId", reasonId)), otherCustomerToken, 403);

        JsonNode notified = call(get("/notifications/my"), provider1Token, 200).get("data");
        assertTrue(types(notified).contains("RESPONSE_ACCEPTED"));
    }

    @Test
    @Order(14)
    void chatAndCallsRequireAnAcceptedOrder() throws Exception {
        Map<String, Object> hello = Map.of("text", "hello", "type", "text", "toUserId", provider1UserId);
        call(post("/messages/insert", hello), customerToken, 200);
        call(post("/messages/insert", Map.of("text", "hello", "type", "text", "toUserId", provider2UserId)), customerToken, 403);
        call(post("/messages/insert", Map.of("text", "hello", "type", "text", "toUserId", customerId)), provider2Token, 403);
        call(post("/messages/insert", Map.of("text", "hi back", "type", "text", "toUserId", customerId)), provider1Token, 200);

        String room = Math.min(customerId, provider1UserId) + "_" + Math.max(customerId, provider1UserId);
        call(post("/agora/token", Map.of("channelName", room, "uid", (int) customerId)), customerToken, 200);
        String forbiddenRoom = Math.min(customerId, provider2UserId) + "_" + Math.max(customerId, provider2UserId);
        call(post("/agora/token", Map.of("channelName", forbiddenRoom, "uid", (int) customerId)), customerToken, 403);
        call(post("/livekit/token", Map.of("roomName", forbiddenRoom, "identity", String.valueOf(customerId), "participantName", "x")), customerToken, 403);

        // presence is hidden from unconnected users
        JsonNode presence = call(get("/presence/online"), provider2Token, 200).get("data");
        assertEquals(0, presence.size());
    }

    @Test
    @Order(15)
    void completingEnablesASingleRatingByTheCustomer() throws Exception {
        Map<String, Object> rate = Map.of("orderId", orderId, "rate", 5, "comment", "great work");
        call(post("/rate/insert", rate), customerToken, 409); // not completed yet
        call(post("/orders/start/" + orderId, Map.of()), provider2Token, 403);
        call(post("/orders/start/" + orderId, Map.of()), provider1Token, 200);
        call(post("/orders/complete/" + orderId, Map.of()), provider1Token, 403);
        call(post("/orders/complete/" + orderId, Map.of()), customerToken, 200);

        call(post("/rate/insert", rate), otherCustomerToken, 403);
        call(post("/rate/insert", Map.of("orderId", orderId, "rate", 9, "comment", "x")), customerToken, 400);
        JsonNode saved = call(post("/rate/insert", rate), customerToken, 200).get("data");
        assertEquals(5.0, saved.get("rate").asDouble());
        call(post("/rate/insert", rate), customerToken, 409); // once per order
        assertEquals(5.0, call(get("/providers/show/" + provider1Id), customerToken, 200).get("data").get("rate").asDouble());
        call(post("/orders/cancel", Map.of("orderId", orderId, "reasonId", reasonId)), customerToken, 409); // completed
    }

    // ------------------------------------------------------------------ declined responses and concurrency

    @Test
    @Order(20)
    void concurrentAcceptsLeaveExactlyOneWinnerAndKeepTheOtherResponsesAsDeclined() throws Exception {
        long order2 = id(call(post("/orders/insert", Map.of(
                "carModelYear", "Civic 2020", "comment", "oil", "date", tomorrow(),
                "servicesIds", List.of(service1),
                "spareParts", List.of(Map.of("sparePartName", "Oil filter")))), otherCustomerToken, 200).get("data"));
        // service 1 is offered by all three providers
        long r1 = id(call(post("/responses/insert", Map.of("orderId", order2, "comment", "a")), provider1Token, 200).get("data"));
        long r2 = id(call(post("/responses/insert", Map.of("orderId", order2, "comment", "b")), provider2Token, 200).get("data"));
        long r3 = id(call(post("/responses/insert", Map.of("orderId", order2, "comment", "c")), provider3Token, 200).get("data"));

        // a provider only ever sees their own response on the order
        JsonNode seenByP2 = call(get("/orders/show/" + order2), provider2Token, 200).get("data");
        assertEquals(1, seenByP2.get("responses").size());
        assertEquals(3, call(get("/orders/show/" + order2), otherCustomerToken, 200).get("data").get("responses").size());

        ExecutorService pool = Executors.newFixedThreadPool(3);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (long target : new long[]{r1, r2, r3}) {
            results.add(pool.submit((Callable<Integer>) () -> {
                start.await();
                return mvc.perform(withAuth(post("/responses/accept-response", Map.of("responseId", target)), otherCustomerToken))
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        int ok = 0, conflict = 0;
        for (Future<Integer> f : results) {
            int status = f.get();
            if (status == 200) ok++;
            else if (status == 409) conflict++;
        }
        pool.shutdown();
        assertEquals(1, ok, "exactly one accept must win");
        assertEquals(2, conflict);

        JsonNode order = call(get("/orders/show/" + order2), otherCustomerToken, 200).get("data");
        assertEquals("ACCEPTED", order.get("status").asText());
        int accepted = 0, declined = 0;
        for (JsonNode r : order.get("responses")) {
            if ("ACCEPTED".equals(r.get("responsesState").asText())) accepted++;
            if ("DECLINED".equals(r.get("responsesState").asText())) declined++;
        }
        assertEquals(1, accepted);
        assertEquals(2, declined); // history preserved, nothing deleted
    }

    // ------------------------------------------------------------------ ownership, direct orders, cancellation, documents

    @Test
    @Order(30)
    void providersCannotTouchEachOthersBranches() throws Exception {
        long branch = id(call(post("/branches/insert", Map.of("name", "Main", "address", "1 Nile St", "providerId", provider2Id)), provider1Token, 200).get("data"));
        assertEquals(provider1Id, call(get("/branches/show/" + branch), provider1Token, 200).get("data").get("provider").get("id").asLong());
        call(post("/branches/update", Map.of("id", branch, "name", "Stolen")), provider2Token, 403);
        call(delete("/branches/delete/" + branch), provider2Token, 403);
        call(post("/work-time/insert", Map.of("branchId", branch, "startTime", "09:00", "closeTime", "17:00", "day", "Monday")), provider2Token, 403);
        call(post("/branches/insert", Map.of("name", "Nope")), customerToken, 403);
        call(post("/providers/update-services", Map.of("providerId", provider1Id, "serviceIds", List.of(service1))), provider2Token, 403);
    }

    @Test
    @Order(31)
    void directOrderGoesToTheSelectedProviderAndCancellationKeepsHistory() throws Exception {
        long direct = id(call(post("/orders/insert", Map.of(
                "carModelYear", "Golf 2015", "comment", "oil", "date", tomorrow(),
                "servicesIds", List.of(service1), "providerId", provider2Id)), customerToken, 200).get("data"));
        assertEquals(provider2Id, call(get("/orders/show/" + direct), customerToken, 200).get("data").get("providerId").asLong());
        call(get("/orders/show/" + direct), provider3Token, 403); // matches the service but was not selected
        long response = id(call(post("/responses/insert", Map.of("orderId", direct, "comment", "sure")), provider2Token, 200).get("data"));

        JsonNode declined = call(post("/responses/cancel-response", Map.of("responseId", response)), customerToken, 200).get("data");
        assertEquals("DECLINED", declined.get("responsesState").asText());
        call(post("/responses/cancel-response", Map.of("responseId", response)), customerToken, 409); // already decided

        call(post("/orders/cancel", Map.of("orderId", direct, "reasonId", reasonId)), customerToken, 200);
        JsonNode mine = call(get("/responses/get-all-response-of-user"), customerToken, 200).get("data");
        boolean kept = false;
        for (JsonNode r : mine) {
            if (r.get("id").asLong() == response) kept = true;
        }
        assertTrue(kept, "declined response must still exist after the order is canceled");
        call(post("/responses/insert", Map.of("orderId", direct, "comment", "late")), provider2Token, 409);
        call(delete("/orders/delete/" + direct), otherCustomerToken, 403);
        call(delete("/orders/delete/" + direct), customerToken, 200);
    }

    @Test
    @Order(32)
    void providerDocumentsArePrivateAndOnlyAdminsAndTheApplicantCanOpenThem() throws Exception {
        String applicantToken = register("applicant@wadii.test", 0, null).get("token").asText();
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        var upload = MockMvcRequestBuilders.multipart("/provider-requests/request")
                .file(new org.springframework.mock.web.MockMultipartFile("frontIdImage", "id.png", "image/png", png))
                .param("name", "New Garage").param("address", "5 Road").param("phoneNumber", "0111")
                .param("serviceIds", String.valueOf(service1));
        JsonNode request = call(upload, applicantToken, 200).get("data");
        long requestId = id(request);
        String stored = request.get("frontIdImage").asText();
        try {
            assertTrue(stored.matches("[0-9a-f-]{36}\\.png"), "server must generate the file name, got " + stored);
            call(get("/users/" + stored), applicantToken, 404); // the public image endpoint never serves documents
            call(get("/users/" + stored), null, 404);
            call(get("/provider-requests/" + requestId + "/document/front-id"), customerToken, 403);
            assertEquals(200, mvc.perform(withAuth(get("/provider-requests/" + requestId + "/document/front-id"), adminToken)).andReturn().getResponse().getStatus());
            assertEquals(200, mvc.perform(withAuth(get("/provider-requests/" + requestId + "/document/front-id"), applicantToken)).andReturn().getResponse().getStatus());
            call(post("/provider-requests/request", Map.of()), applicantToken, 415); // not multipart
            call(post("/provider-requests/accept/" + requestId, Map.of()), applicantToken, 403);

            call(post("/provider-requests/accept/" + requestId, Map.of()), adminToken, 200);
            assertEquals("PROVIDER", call(get("/users/me"), applicantToken, 200).get("data").get("role").asText());
        } finally {
            java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get("uploads", "private", stored));
        }
    }

    @Test
    @Order(33)
    void textFilesCannotBeUploadedAsImages() throws Exception {
        var upload = MockMvcRequestBuilders.multipart("/users/upload-image")
                .file(new org.springframework.mock.web.MockMultipartFile("file", "evil.html", "text/html", "<script>1</script>".getBytes()));
        call(upload, customerToken, 400);
        var disguised = MockMvcRequestBuilders.multipart("/users/upload-image")
                .file(new org.springframework.mock.web.MockMultipartFile("file", "evil.png", "image/png", "<script>1</script>".getBytes()));
        call(disguised, customerToken, 400);
    }

    // ------------------------------------------------------------------ helpers

    private String tomorrow() {
        return LocalDate.now().plusDays(1).toString();
    }

    private Map<String, Object> registration(String email, int userType, String providerName) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("firstName", "First");
        body.put("lastName", "Last");
        body.put("email", email);
        body.put("password", "Secret123");
        body.put("phone", "0100000000");
        body.put("fcmToken", "");
        body.put("cityId", cityId);
        body.put("userType", userType);
        body.put("providerName", providerName == null ? "" : providerName);
        return body;
    }

    private JsonNode register(String email, int userType, String providerName) throws Exception {
        return call(post("/auth/register", registration(email, userType, providerName)), null, 200).get("data");
    }

    private String login(String email, String password) throws Exception {
        return call(post("/auth/login", Map.of("email", email, "password", password, "fcmToken", "")), null, 200)
                .get("data").get("token").asText();
    }

    private long providerId(String token) throws Exception {
        return call(get("/providers/me"), token, 200).get("data").get("id").asLong();
    }

    private void setServices(String token, long providerId, long... serviceIds) throws Exception {
        List<Long> ids = new ArrayList<>();
        for (long s : serviceIds) ids.add(s);
        call(post("/providers/update-services", Map.of("providerId", providerId, "serviceIds", ids)), token, 200);
    }

    private long id(JsonNode node) {
        return node.get("id").asLong();
    }

    private List<String> types(JsonNode notifications) {
        List<String> types = new ArrayList<>();
        notifications.forEach(n -> types.add(n.get("type").asText()));
        return types;
    }

    private MockHttpServletRequestBuilder get(String url) {
        return MockMvcRequestBuilders.get(url);
    }

    private MockHttpServletRequestBuilder delete(String url) {
        return MockMvcRequestBuilders.delete(url);
    }

    private MockHttpServletRequestBuilder post(String url, Object body) throws Exception {
        return MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
    }

    private MockHttpServletRequestBuilder withAuth(MockHttpServletRequestBuilder builder, String token) {
        return token == null ? builder : builder.header("Authorization", "Bearer " + token);
    }

    /** Performs the request, asserts the HTTP status and returns the parsed JSON body. */
    private JsonNode call(MockHttpServletRequestBuilder builder, String token, int expectedStatus) throws Exception {
        var response = mvc.perform(withAuth(builder, token)).andReturn().getResponse();
        String content = response.getContentAsString();
        assertEquals(expectedStatus, response.getStatus(), "unexpected status, body: " + content);
        return content.isBlank() ? mapper.createObjectNode() : mapper.readTree(content);
    }
}
