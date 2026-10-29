package com.doubleclick.wadii.identity;

import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.identity.dto.AuthResponse;
import com.doubleclick.wadii.identity.dto.ChangePasswordRequest;
import com.doubleclick.wadii.identity.dto.LoginRequest;
import com.doubleclick.wadii.identity.dto.RegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code /auth/*} is kept as an alias of {@code /api/v1/auth/*} for existing clients. */
@RestController
@RequestMapping({"/api/v1/auth", "/auth"})
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.created(authService.register(request), "Registered successfully");
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request), "Logged in");
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@AuthenticationPrincipal AuthUser me,
                                                            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(me, request);
        return ApiResponse.ok(null, "Password changed; log in again on every device");
    }

    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<Void>> logoutAll(@AuthenticationPrincipal AuthUser me) {
        authService.logoutEverywhere(me);
        return ApiResponse.ok(null, "All sessions revoked");
    }
}
