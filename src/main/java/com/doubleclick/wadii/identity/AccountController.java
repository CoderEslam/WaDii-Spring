package com.doubleclick.wadii.identity;

import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService service;

    public record PushTokenRequest(@Size(max = 512) String token) {
    }

    @PostMapping("/auth/register")
    public ResponseEntity<ApiResponse<AccountService.AuthResponse>> register(@Valid @RequestBody AccountService.RegisterRequest body) {
        return ApiResponse.created(service.register(body), "Registered");
    }

    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponse<AccountService.AuthResponse>> login(@Valid @RequestBody AccountService.LoginRequest body) {
        return ApiResponse.ok(service.login(body));
    }

    @PostMapping("/auth/logout-all")
    public ResponseEntity<ApiResponse<Void>> logoutAll(@AuthenticationPrincipal AuthUser me) {
        service.logoutEverywhere(me);
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AccountService.Me>> me(@AuthenticationPrincipal AuthUser me) {
        return ApiResponse.ok(service.me(me.id()));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<AccountService.Me>> update(@AuthenticationPrincipal AuthUser me,
                                                                 @Valid @RequestBody AccountService.UpdateMeRequest body) {
        return ApiResponse.ok(service.update(me, body));
    }

    @PostMapping("/me/password")
    public ResponseEntity<ApiResponse<AccountService.AuthResponse>> password(@AuthenticationPrincipal AuthUser me,
                                                                             @Valid @RequestBody AccountService.ChangePasswordRequest body) {
        return ApiResponse.ok(service.changePassword(me, body));
    }

    @PutMapping("/me/push-token")
    public ResponseEntity<ApiResponse<Void>> pushToken(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody PushTokenRequest body) {
        service.setPushToken(me, body.token());
        return ApiResponse.ok(null);
    }
}
