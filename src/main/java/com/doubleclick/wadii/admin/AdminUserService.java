package com.doubleclick.wadii.admin;

import com.doubleclick.wadii.audit.AuditService;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.identity.AccountService;
import com.doubleclick.wadii.identity.AccountStatus;
import com.doubleclick.wadii.identity.AdminRole;
import com.doubleclick.wadii.identity.User;
import com.doubleclick.wadii.identity.UserRepository;
import com.doubleclick.wadii.notification.NotificationService;
import com.doubleclick.wadii.notification.NotificationType;
import com.doubleclick.wadii.provider.Provider;
import com.doubleclick.wadii.provider.ProviderRepository;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** Account moderation and staff accounts. Suspension revokes every token at once; nothing is deleted. */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository users;
    private final ProviderRepository providers;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notifications;
    private final AuditService audit;

    public record AdminUserView(Long id, String fullName, String email, String phone, AccountStatus status, boolean admin,
                                Set<AdminRole> adminRoles, Long providerId, Provider.ApplicationStatus applicationStatus,
                                String statusReason, Instant createdAt) {
    }

    public record ReasonInput(@NotBlank @Size(max = 500) String reason) {
    }

    public record CreateAdminInput(@NotBlank @Size(max = 120) String fullName, @NotBlank @Email String email,
                                   @NotBlank @Pattern(regexp = AccountService.PHONE_PATTERN) String phone,
                                   @NotBlank @Size(min = 12, max = 72) String password, @NotEmpty Set<AdminRole> roles) {
    }

    public record RolesInput(@NotEmpty Set<AdminRole> roles) {
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserView> list(String q, AccountStatus status, Integer page, Integer size) {
        String pattern = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        return PageResponse.of(users.search(pattern, status, PageResponse.pageable(page, size)), this::view);
    }

    @Transactional
    public AdminUserView changeStatus(AuthUser admin, Long userId, AccountStatus target, String reason) {
        User u = users.findById(userId).orElseThrow(() -> BusinessException.of(ErrorCode.NOT_FOUND));
        if (u.getId().equals(admin.id())) {
            throw BusinessException.validation("You cannot change your own account status.");
        }
        if (u.isAdmin() && !admin.hasAdminRole(AdminRole.SUPER_ADMIN)) {
            throw BusinessException.forbidden("Only a super admin can change another staff account.");
        }
        u.setStatus(target);
        u.setStatusReason(reason);
        if (target != AccountStatus.ACTIVE) {
            u.revokeTokens();
        }
        audit.record("USER_" + target, "USER", userId, reason);
        notifications.notify(userId, NotificationType.ACCOUNT_STATUS, "Account " + target.name().toLowerCase(Locale.ROOT),
                target == AccountStatus.ACTIVE ? "Your account is active again." : "Reason: " + reason, "USER", userId);
        return view(u);
    }

    @Transactional
    public AdminUserView createAdmin(AuthUser superAdmin, CreateAdminInput input) {
        String email = input.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw BusinessException.of(ErrorCode.EMAIL_ALREADY_USED);
        }
        if (users.existsByPhone(input.phone())) {
            throw BusinessException.of(ErrorCode.PHONE_ALREADY_USED);
        }
        User u = new User();
        u.setFullName(input.fullName().trim());
        u.setEmail(email);
        u.setPhone(input.phone());
        u.setPasswordHash(passwordEncoder.encode(input.password()));
        u.setAdmin(true);
        u.setAdminRoles(EnumSet.copyOf(input.roles()));
        users.save(u);
        audit.record("ADMIN_CREATED", "USER", u.getId(), input.roles().toString());
        return view(u);
    }

    @Transactional
    public AdminUserView setRoles(AuthUser superAdmin, Long userId, Set<AdminRole> roles) {
        User u = users.findById(userId).filter(User::isAdmin).orElseThrow(() -> BusinessException.of(ErrorCode.NOT_FOUND));
        if (u.getId().equals(superAdmin.id()) && !roles.contains(AdminRole.SUPER_ADMIN)) {
            throw BusinessException.validation("You cannot remove your own super admin role.");
        }
        u.setAdminRoles(EnumSet.copyOf(roles));
        u.revokeTokens();
        audit.record("ADMIN_ROLES_CHANGED", "USER", userId, roles.toString());
        return view(u);
    }

    private AdminUserView view(User u) {
        var provider = providers.findByUserId(u.getId()).orElse(null);
        return new AdminUserView(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getStatus(), u.isAdmin(), u.getAdminRoles(),
                provider == null ? null : provider.getId(), provider == null ? null : provider.getApplicationStatus(),
                u.getStatusReason(), u.getCreatedAt());
    }
}
