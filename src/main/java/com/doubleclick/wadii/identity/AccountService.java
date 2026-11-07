package com.doubleclick.wadii.identity;

import com.doubleclick.wadii.audit.AuditService;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.common.security.JwtService;
import com.doubleclick.wadii.common.security.RequestInfo;
import com.doubleclick.wadii.provider.Provider;
import com.doubleclick.wadii.provider.ProviderRepository;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;

/** Registration, login, password changes and the caller's own profile and capabilities. */
@Service
@RequiredArgsConstructor
public class AccountService {

    /** Egyptian mobile numbers, local format. */
    public static final String PHONE_PATTERN = "^01[0125][0-9]{8}$";

    private final UserRepository users;
    private final ProviderRepository providers;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginRateLimiter rateLimiter;
    private final AuditService audit;

    /** Spent on unknown emails so a login takes the same time whether or not the account exists. */
    private volatile String dummyHash;

    public record RegisterRequest(@NotBlank @Size(max = 120) String fullName,
                                  @NotBlank @Email @Size(max = 255) String email,
                                  @NotBlank @Pattern(regexp = PHONE_PATTERN, message = "must be an Egyptian mobile number") String phone,
                                  @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record UpdateMeRequest(@Size(min = 1, max = 120) String fullName,
                                  @Pattern(regexp = PHONE_PATTERN, message = "must be an Egyptian mobile number") String phone) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    public record ProviderCapability(Long providerId, Provider.ApplicationStatus applicationStatus,
                                     Provider.Status providerStatus, boolean canOperate) {
    }

    public record Capabilities(boolean customer, boolean seller, ProviderCapability provider) {
    }

    public record Me(Long id, String fullName, String email, String phone, AccountStatus status, boolean admin,
                     Set<AdminRole> adminRoles, Capabilities capabilities, Instant createdAt) {
    }

    public record AuthResponse(String token, long expiresInSeconds, Me user) {
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        rateLimiter.checkRegister(RequestInfo.clientIp());
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw BusinessException.of(ErrorCode.EMAIL_ALREADY_USED);
        }
        if (users.existsByPhone(request.phone())) {
            throw BusinessException.of(ErrorCode.PHONE_ALREADY_USED);
        }
        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPhone(request.phone());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        users.save(user);
        audit.record("USER_REGISTERED", "USER", user.getId(), null);
        return authResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        rateLimiter.checkLogin(RequestInfo.clientIp());
        User user = users.findByEmailIgnoreCase(request.email().trim()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash());
            throw BusinessException.of(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw BusinessException.of(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!user.isActive()) {
            throw BusinessException.of(ErrorCode.ACCOUNT_SUSPENDED);
        }
        return authResponse(user);
    }

    @Transactional(readOnly = true)
    public Me me(Long userId) {
        return toMe(user(userId));
    }

    @Transactional
    public Me update(AuthUser me, UpdateMeRequest request) {
        User user = user(me.id());
        if (request.fullName() != null) {
            user.setFullName(request.fullName().trim());
        }
        if (request.phone() != null && !request.phone().equals(user.getPhone())) {
            if (users.existsByPhone(request.phone())) {
                throw BusinessException.of(ErrorCode.PHONE_ALREADY_USED);
            }
            user.setPhone(request.phone());
        }
        return toMe(user);
    }

    @Transactional
    public AuthResponse changePassword(AuthUser me, ChangePasswordRequest request) {
        User user = user(me.id());
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw BusinessException.of(ErrorCode.INVALID_CREDENTIALS, "The current password is wrong.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.revokeTokens();
        users.saveAndFlush(user);
        audit.record("PASSWORD_CHANGED", "USER", user.getId(), null);
        return authResponse(user);
    }

    @Transactional
    public void logoutEverywhere(AuthUser me) {
        user(me.id()).revokeTokens();
        audit.record("LOGOUT_ALL", "USER", me.id(), null);
    }

    @Transactional
    public void setPushToken(AuthUser me, String token) {
        user(me.id()).setFcmToken(token == null || token.isBlank() ? null : token.trim());
    }

    public Me toMe(User user) {
        ProviderCapability provider = providers.findByUserId(user.getId())
                .map(p -> new ProviderCapability(p.getId(), p.getApplicationStatus(), p.getProviderStatus(),
                        p.canOperate() && user.isActive()))
                .orElse(null);
        boolean regular = !user.isAdmin();
        return new Me(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(), user.getStatus(), user.isAdmin(),
                user.effectiveAdminRoles(), new Capabilities(regular, regular, regular ? provider : null), user.getCreatedAt());
    }

    private AuthResponse authResponse(User user) {
        return new AuthResponse(jwtService.issue(user), jwtService.expiresInSeconds(), toMe(user));
    }

    private User user(Long id) {
        return users.findById(id).orElseThrow(() -> BusinessException.of(ErrorCode.NOT_FOUND));
    }

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode("timing-equalizer-password");
        }
        return dummyHash;
    }
}
