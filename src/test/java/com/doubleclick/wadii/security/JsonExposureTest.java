package com.doubleclick.wadii.security;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.entities.Order;
import com.doubleclick.wadii.entities.OrderStatus;
import com.doubleclick.wadii.entities.Provider;
import com.doubleclick.wadii.entities.Responses;
import com.doubleclick.wadii.entities.ResponsesState;
import com.doubleclick.wadii.entities.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Guards what the API exposes: secrets never leave through a serialized User, and providers only see their own response. */
class JsonExposureTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private User user(long id) {
        User user = new User();
        user.setId(id);
        user.setEmail("u" + id + "@example.com");
        user.setPassword("$2a$10$hash");
        user.setToken("jwt-token");
        user.setFcmToken("fcm-token");
        user.setRole(Role.USER);
        return user;
    }

    @Test
    void userNeverExposesPasswordTokenOrFcmToken() throws Exception {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(user(1)));
        assertFalse(json.has("password"));
        assertFalse(json.has("token"));
        assertFalse(json.has("fcmToken"));
        assertEquals("u1@example.com", json.get("email").asText());
    }

    @Test
    void userCanStillBeReadFromRequestBodies() throws Exception {
        User read = mapper.readValue("{\"email\":\"a@b.co\",\"password\":\"secret\",\"fcmToken\":\"t\"}", User.class);
        assertEquals("secret", read.getPassword());
        assertEquals("t", read.getFcmToken());
    }

    @Test
    void orderShowsAllResponsesUnlessAViewIsSet() throws Exception {
        Order order = new Order();
        order.setId(5L);
        order.setStatus(OrderStatus.RESPONSES_AVAILABLE);
        Responses mine = response(1L);
        Responses other = response(2L);
        order.setResponses(List.of(mine, other));

        JsonNode all = mapper.readTree(mapper.writeValueAsString(order));
        assertEquals(2, all.get("responses").size());

        order.setResponsesView(List.of(mine));
        JsonNode filtered = mapper.readTree(mapper.writeValueAsString(order));
        assertEquals(1, filtered.get("responses").size());
        assertEquals(1L, filtered.get("responses").get(0).get("id").asLong());
    }

    @Test
    void orderExposesTargetAndAcceptedProviderIdsOnly() throws Exception {
        Order order = new Order();
        Provider provider = new Provider();
        provider.setId(9L);
        order.setProvider(provider);
        order.setAcceptedProvider(provider);
        JsonNode json = mapper.readTree(mapper.writeValueAsString(order));
        assertEquals(9L, json.get("providerId").asLong());
        assertEquals(9L, json.get("acceptedProviderId").asLong());
        assertFalse(json.has("provider"));
        assertFalse(json.has("acceptedProvider"));
    }

    private Responses response(long id) {
        Responses response = new Responses();
        response.setId(id);
        response.setResponsesState(ResponsesState.PENDING);
        return response;
    }
}
