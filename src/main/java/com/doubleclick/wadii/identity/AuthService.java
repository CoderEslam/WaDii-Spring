package com.doubleclick.wadii.identity;

import com.doubleclick.wadii.audit.AuditAction;
import com.doubleclick.wadii.audit.AuditService;
import com.doubleclick.wadii.catalog.City;
import com.doubleclick.wadii.catalog.CityRepository;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.common.security.JwtService;
import com.doubleclick.wadii.identity.dto.AuthResponse;
import com.doubleclick.wadii.identity.dto.ChangePasswordRequest;
import com.doubleclick.wadii.identity.dto.LoginRequest;
import com.doubleclick.wadii.identity.dto.RegisterRequest;
import com.doubleclick.wadii.identity.dto.UserView;
import com.doubleclick.wadii.order.ActorRole;
import com.doubleclick.wadii.provider.Provider;
import com.doubleclick.wadii.provider.ProviderRepository;
import com.doubleclick.wadii.provider.ProviderStats;
import com.doubleclick.wadii.provider.ProviderStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class AuthService {

    /** Used to spend the same BCrypt time for unknown emails, so accounts cannot be enumerated by timing. */
    private volatile String dummyHash;

    private final UserRepository userRepository;
    private final CityRepository cityRepository;
    private final ProviderRepository providerRepository;
    private final ProviderStatsRepository providerStatsRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.userType() == Role.ADMIN.ordinal()) {
            throw BusinessException.of(ErrorCode.ADMIN_REGISTRATION_FORBIDDEN);
        }
        if (request.userType() != Role.USER.ordinal() && request.userType() != Role.PROVIDER.ordinal()) {
            throw BusinessException.validation("userType must be 0 (user) or 1 (provider)");
        }
        boolean provider = request.userType() == Role.PROVIDER.ordinal();
        if (provider && (request.providerName() == null || request.providerName().isBlank())) {
            throw BusinessException.validation("providerName is required for providers");
        }
        String email = request.email().trim();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw BusinessException.of(ErrorCode.EMAIL_ALREADY_USED);
        }
        City city = cityRepository.findById(request.cityId())
                .orElseThrow(() -> BusinessException.validation("cityId does not exist"));

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName() == null ? null : request.lastName().trim());
        user.setPhone(request.phone());
        user.setCity(city);
        user.setRole(provider ? Role.PROVIDER : Role.USER);
        user.setFcmToken(blankToNull(request.fcmToken()));
        user = userRepository.save(user);

        Long providerId = null;
        if (provider) {
            Provider p = new Provider();
            p.setUser(user);
            p.setName(request.providerName().trim());
            p = providerRepository.save(p);
            providerStatsRepository.save(new ProviderStats(p.getId()));
            providerId = p.getId();
        }
        auditService.record(AuthUser.of(user), provider ? ActorRole.PROVIDER : ActorRole.CUSTOMER,
                AuditAction.REGISTER, "USER", user.getId(), "role=" + user.getRole());
        return new AuthResponse(jwtService.issue(user), "Bearer", jwtService.expiresInSeconds(), UserView.of(user), providerId);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash());
            auditService.securityEvent(null, ActorRole.SYSTEM, AuditAction.LOGIN_FAILED, "unknown email");
            throw BusinessException.of(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditService.securityEvent(user.getId(), AuditService.roleOf(AuthUser.of(user)), AuditAction.LOGIN_FAILED, "wrong password");
            throw BusinessException.of(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!user.isActive()) {
            throw BusinessException.of(ErrorCode.ACCOUNT_SUSPENDED);
        }
        if (request.fcmToken() != null && !request.fcmToken().isBlank()) {
            user.setFcmToken(request.fcmToken());
        }
        user.setLastLoginAt(clock.instant());
        AuthUser actor = AuthUser.of(user);
        auditService.record(actor, AuditService.roleOf(actor), AuditAction.LOGIN, "USER", user.getId(), null);
        Long providerId = user.getRole() == Role.PROVIDER
                ? providerRepository.findByUserId(user.getId()).map(Provider::getId).orElse(null) : null;
        return new AuthResponse(jwtService.issue(user), "Bearer", jwtService.expiresInSeconds(), UserView.of(user), providerId);
    }

    @Transactional
    public void changePassword(AuthUser me, ChangePasswordRequest request) {
        User user = userRepository.findById(me.id()).orElseThrow(() -> BusinessException.of(ErrorCode.UNAUTHENTICATED));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw BusinessException.of(ErrorCode.INVALID_CREDENTIALS, "The current password is wrong.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.revokeTokens();
        auditService.record(me, AuditService.roleOf(me), AuditAction.ADMIN_ACTION, "USER", user.getId(), "password changed");
    }

    /** Invalidates every token of the caller (all devices). */
    @Transactional
    public void logoutEverywhere(AuthUser me) {
        User user = userRepository.findById(me.id()).orElseThrow(() -> BusinessException.of(ErrorCode.UNAUTHENTICATED));
        user.revokeTokens();
        user.setFcmToken(null);
    }

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode(java.util.UUID.randomUUID().toString());
        }
        return dummyHash;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
