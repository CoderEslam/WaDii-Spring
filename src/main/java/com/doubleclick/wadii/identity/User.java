package com.doubleclick.wadii.identity;

import com.doubleclick.wadii.common.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One account per person. Customer and seller capabilities are always present; provider capability comes from a
 * linked provider profile; staff accounts are flagged {@code admin} and can only be created by a super admin.
 */
@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends AuditedEntity {

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "is_admin", nullable = false)
    private boolean admin;

    @Column(name = "admin_roles")
    private String adminRolesCsv;

    /** Bumped to revoke every issued JWT at once (logout everywhere, password change, suspension). */
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Column(name = "fcm_token")
    private String fcmToken;

    @Column(name = "status_reason")
    private String statusReason;

    @Version
    private long version;

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public void revokeTokens() {
        tokenVersion++;
    }

    public Set<AdminRole> getAdminRoles() {
        if (adminRolesCsv == null || adminRolesCsv.isBlank()) {
            return EnumSet.noneOf(AdminRole.class);
        }
        return Arrays.stream(adminRolesCsv.split(",")).map(AdminRole::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(AdminRole.class)));
    }

    public void setAdminRoles(Set<AdminRole> roles) {
        adminRolesCsv = roles == null || roles.isEmpty() ? null
                : roles.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    /** SUPER_ADMIN expands to every admin role so permission checks stay one-dimensional. */
    public Set<AdminRole> effectiveAdminRoles() {
        if (!admin) {
            return EnumSet.noneOf(AdminRole.class);
        }
        Set<AdminRole> roles = getAdminRoles();
        return roles.contains(AdminRole.SUPER_ADMIN) ? EnumSet.allOf(AdminRole.class) : roles;
    }
}
