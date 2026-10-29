package com.doubleclick.wadii;

import com.doubleclick.wadii.support.IntegrationTest;
import com.doubleclick.wadii.support.TestApi;
import com.doubleclick.wadii.support.TestApi.Actor;
import com.doubleclick.wadii.support.TestApi.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.Set;

import static com.doubleclick.wadii.support.TestApi.expectOk;
import static com.doubleclick.wadii.support.TestApi.expectStatus;
import static org.assertj.core.api.Assertions.assertThat;

class AuthSecurityTest extends IntegrationTest {

    @Autowired
    TestApi api;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void registrationHashesPasswordAndNeverReturnsIt() {
        String email = TestApi.uniqueEmail("reg");
        Result r = expectStatus(201, api.register(email, 0, null));
        assertThat(r.data().path("token").asText()).isNotBlank();
        assertThat(r.body().toString()).doesNotContain("Password123").doesNotContainIgnoringCase("passwordHash");
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, email);
        assertThat(hash).startsWith("$2").isNotEqualTo("Password123");
    }

    @Test
    void duplicateEmailIsConflict() {
        String email = TestApi.uniqueEmail("dup");
        expectStatus(201, api.register(email, 0, null));
        Result again = api.register(email.toUpperCase(), 0, null);
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.code()).isEqualTo("EMAIL_ALREADY_USED");
    }

    @Test
    void adminRegistrationIsBlocked() {
        Result r = api.register(TestApi.uniqueEmail("evil"), 2, null);
        assertThat(r.status()).isEqualTo(403);
        assertThat(r.code()).isEqualTo("ADMIN_REGISTRATION_FORBIDDEN");
        assertThat(r.body().path("success").asBoolean()).isFalse();
    }

    @Test
    void loginWithInvalidCredentialsIs401() {
        Actor c = api.customer();
        Result wrong = api.post("/api/v1/auth/login", null, Map.of("email", c.email(), "password", "nope-nope"));
        assertThat(wrong.status()).isEqualTo(401);
        assertThat(wrong.code()).isEqualTo("INVALID_CREDENTIALS");
        Result unknown = api.post("/api/v1/auth/login", null, Map.of("email", "nobody@test.wadii", "password", "whatever1"));
        assertThat(unknown.status()).isEqualTo(401);
        assertThat(unknown.code()).isEqualTo("INVALID_CREDENTIALS");
        expectOk(api.post("/auth/login", null, Map.of("email", c.email(), "password", "Password123")));
    }

    @Test
    void protectedEndpointsNeedAValidToken() {
        assertThat(api.get("/api/v1/users/me", null).status()).isEqualTo(401);
        Result bad = api.get("/api/v1/users/me", new Actor("not-a-jwt", 0, null, null));
        assertThat(bad.status()).isEqualTo(401);
        assertThat(bad.code()).isEqualTo("TOKEN_INVALID");
        assertThat(api.get("/api/v1/catalog/services", null).status()).isEqualTo(200);
    }

    @Test
    void normalUsersCannotReachAdminEndpoints() {
        Actor c = api.customer();
        assertThat(api.get("/api/v1/admin/users", c).status()).isEqualTo(403);
        assertThat(api.get("/api/v1/admin/payments", c).status()).isEqualTo(403);
        assertThat(api.post("/api/v1/catalog/services", c, Map.of("name", "x")).status()).isEqualTo(403);
        Integer failures = jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'AUTHORIZATION_FAILURE' AND actor_user_id = ?",
                Integer.class, c.userId());
        assertThat(failures).isGreaterThanOrEqualTo(3);
    }

    @Test
    void adminPermissionsAreGranular() {
        Actor superAdmin = api.admin();
        String email = TestApi.uniqueEmail("content");
        expectStatus(201, api.post("/api/v1/admin/users/admins", superAdmin, Map.of("firstName", "Content", "email", email,
                "password", "ContentAdmin123!", "adminRoles", Set.of("CONTENT_ADMIN"))));
        Result login = expectOk(api.post("/api/v1/auth/login", null, Map.of("email", email, "password", "ContentAdmin123!")));
        Actor content = new Actor(login.data().path("token").asText(), login.data().path("user").path("id").asLong(), null, email);
        assertThat(api.post("/api/v1/catalog/services", content, Map.of("name", "svc-" + email)).status()).isEqualTo(201);
        assertThat(api.get("/api/v1/admin/payments", content).status()).isEqualTo(403);
        assertThat(api.post("/api/v1/admin/users/admins", content, Map.of("firstName", "X", "email", TestApi.uniqueEmail("x"),
                "password", "Whatever12345!", "adminRoles", Set.of("SUPER_ADMIN"))).status()).isEqualTo(403);
    }

    @Test
    void logoutEverywhereRevokesTokens() {
        Actor c = api.customer();
        expectOk(api.get("/api/v1/users/me", c));
        expectOk(api.post("/api/v1/auth/logout-all", c, null));
        assertThat(api.get("/api/v1/users/me", c).status()).isEqualTo(401);
    }

    @Test
    void suspendedAccountsAreLockedOut() {
        Actor c = api.customer();
        expectOk(api.put("/api/v1/admin/users/" + c.userId() + "/status", api.admin(), Map.of("status", "SUSPENDED", "reason", "test")));
        assertThat(api.get("/api/v1/users/me", c).status()).isEqualTo(401);
        Result login = api.post("/api/v1/auth/login", null, Map.of("email", c.email(), "password", "Password123"));
        assertThat(login.status()).isEqualTo(403);
        assertThat(login.code()).isEqualTo("ACCOUNT_SUSPENDED");
    }

    @Test
    void userProfileIsAlwaysTheCaller() {
        Actor a = api.customer();
        Result me = expectOk(api.get("/api/v1/users/me", a));
        assertThat(me.data().path("id").asLong()).isEqualTo(a.userId());
        expectOk(api.patch("/api/v1/users/me", a, Map.of("firstName", "Renamed")));
        assertThat(api.get("/api/v1/users/me", a).data().path("firstName").asText()).isEqualTo("Renamed");
    }

    @Test
    void uploadsValidateSignature() {
        Actor a = api.customer();
        Result fake = api.multipart("/api/v1/users/me/image", a, "x.png", "image/png", "not an image".getBytes(), Map.of());
        assertThat(fake.status()).isEqualTo(400);
        assertThat(fake.code()).isEqualTo("FILE_CONTENT_MISMATCH");
        Result exe = api.multipart("/api/v1/users/me/image", a, "x.exe", "application/octet-stream", TestApi.PNG, Map.of());
        assertThat(exe.code()).isEqualTo("FILE_TYPE_NOT_ALLOWED");
        Result ok = expectOk(api.multipart("/api/v1/users/me/image", a, "me.png", "image/png", TestApi.PNG, Map.of()));
        String key = ok.data().path("image").asText();
        assertThat(key).matches("[0-9a-f\\-]{36}\\.png");
        assertThat(api.rawStatus(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/files/public/" + key), null)).isEqualTo(200);
    }
}
