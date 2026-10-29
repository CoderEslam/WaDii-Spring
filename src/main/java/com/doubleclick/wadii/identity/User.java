package com.doubleclick.wadii.identity;

import com.doubleclick.wadii.catalog.City;
import com.doubleclick.wadii.common.persistence.AuditedEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends AuditedEntity {

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    private String firstName;
    private String lastName;
    private String phone;
    private String image;
    private String backgroundImage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id")
    private City city;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status = AccountStatus.ACTIVE;

    private String fcmToken;

    /** Incrementing it invalidates every JWT issued before. */
    @Column(nullable = false)
    private int tokenVersion;

    private Instant lastLoginAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_admin_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "admin_role")
    @Enumerated(EnumType.STRING)
    private Set<AdminRole> adminRoles = new HashSet<>();

    public String displayName() {
        String name = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
        return name.isEmpty() ? "User #" + getId() : name;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    /** Effective admin permissions: SUPER_ADMIN expands to all of them; non-admins have none. */
    public Set<AdminRole> effectiveAdminRoles() {
        if (role != Role.ADMIN) return EnumSet.noneOf(AdminRole.class);
        if (adminRoles.contains(AdminRole.SUPER_ADMIN)) return EnumSet.allOf(AdminRole.class);
        return adminRoles.isEmpty() ? EnumSet.noneOf(AdminRole.class) : EnumSet.copyOf(adminRoles);
    }

    public void revokeTokens() {
        tokenVersion++;
    }
}
