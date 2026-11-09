package com.doubleclick.wadii;

import com.doubleclick.wadii.support.IntegrationTest;
import com.doubleclick.wadii.support.TestApi.Actor;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Accounts, login and token revocation (business rules section 3.2). */
class AccountSpecTest extends IntegrationTest {

    @Test
    void registrationCreatesCustomerAndSellerCapabilitiesWithoutProvider() {
        Actor user = api.customer();
        var me = api.get("/api/v1/me", user).ok().data();
        assertThat(me.path("admin").asBoolean()).isFalse();
        assertThat(me.path("capabilities").path("customer").asBoolean()).isTrue();
        assertThat(me.path("capabilities").path("seller").asBoolean()).isTrue();
        assertThat(me.path("capabilities").has("provider")).isFalse();
    }

    @Test
    void publicRegistrationCannotCreateAnAdmin() {
        var r = api.post("/api/v1/auth/register", null, Map.of("fullName", "Evil", "email", "evil-admin@wadii.test",
                "phone", "01299999999", "password", "Password123", "admin", true, "adminRoles", new String[]{"SUPER_ADMIN"})).ok();
        assertThat(r.data().path("user").path("admin").asBoolean()).isFalse();
        assertThat(r.data().path("user").path("adminRoles")).isEmpty();
    }

    @Test
    void duplicateEmailAndWeakPasswordAreRejected() {
        Actor user = api.customer();
        var dup = api.post("/api/v1/auth/register", null, Map.of("fullName", "Dup", "email", user.email(),
                "phone", "01288888888", "password", "Password123"));
        assertThat(dup.status()).isEqualTo(409);
        assertThat(dup.code()).isEqualTo("EMAIL_ALREADY_USED");
        var weak = api.post("/api/v1/auth/register", null, Map.of("fullName", "Weak", "email", "weak@wadii.test",
                "phone", "01277777777", "password", "short"));
        assertThat(weak.status()).isEqualTo(400);
        assertThat(weak.code()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameError() {
        Actor user = api.customer();
        var wrong = api.post("/api/v1/auth/login", null, Map.of("email", user.email(), "password", "nope-nope"));
        var unknown = api.post("/api/v1/auth/login", null, Map.of("email", "nobody@wadii.test", "password", "nope-nope"));
        assertThat(wrong.status()).isEqualTo(401);
        assertThat(wrong.code()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(unknown.code()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void logoutEverywhereRevokesExistingTokens() {
        Actor user = api.customer();
        api.post("/api/v1/auth/logout-all", user, null).ok();
        var after = api.get("/api/v1/me", user);
        assertThat(after.status()).isEqualTo(401);
        assertThat(after.code()).isEqualTo("TOKEN_INVALID");
    }

    @Test
    void passwordChangeRevokesOldTokensAndReturnsANewOne() {
        Actor user = api.customer();
        var r = api.post("/api/v1/me/password", user, Map.of("currentPassword", "Password123", "newPassword", "NewPassword456")).ok();
        assertThat(api.get("/api/v1/me", user).status()).isEqualTo(401);
        Actor renewed = new Actor(r.data().path("token").asText(), user.userId(), null, user.email());
        api.get("/api/v1/me", renewed).ok();
    }

    @Test
    void suspendedAccountCannotLogInOrUseTokens() {
        Actor user = api.customer();
        api.post("/api/v1/admin/users/" + user.userId() + "/suspend", api.admin(), Map.of("reason", "fraud review")).ok();
        assertThat(api.get("/api/v1/me", user).status()).isEqualTo(401);
        var login = api.post("/api/v1/auth/login", null, Map.of("email", user.email(), "password", "Password123"));
        assertThat(login.status()).isEqualTo(403);
        assertThat(login.code()).isEqualTo("ACCOUNT_SUSPENDED");
    }

    @Test
    void protectedEndpointsNeedAToken() {
        assertThat(api.get("/api/v1/me", null).status()).isEqualTo(401);
        assertThat(api.get("/api/v1/catalog/categories", null).status()).isEqualTo(200);
        assertThat(api.get("/api/v1/config/public", null).ok().data().path("responseDeadlineHours").asInt()).isEqualTo(48);
    }
}
