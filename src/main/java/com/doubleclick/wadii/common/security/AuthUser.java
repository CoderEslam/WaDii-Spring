package com.doubleclick.wadii.common.security;

import com.doubleclick.wadii.identity.AdminRole;
import com.doubleclick.wadii.identity.Role;
import com.doubleclick.wadii.identity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The authenticated caller, built from the JWT subject and reloaded from the database on every request.
 * Services take this instead of any client-supplied user id.
 */
public record AuthUser(Long id, String email, Role role, Set<AdminRole> adminRoles) {

    public static AuthUser of(User user) {
        return new AuthUser(user.getId(), user.getEmail(), user.getRole(), user.effectiveAdminRoles());
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isProvider() {
        return role == Role.PROVIDER;
    }

    public boolean hasAdminRole(AdminRole adminRole) {
        return isAdmin() && adminRoles.contains(adminRole);
    }

    /** Support or super admins may look into any order, conversation or dispute. */
    public boolean isSupport() {
        return hasAdminRole(AdminRole.SUPPORT_ADMIN);
    }

    public boolean isFinance() {
        return hasAdminRole(AdminRole.FINANCE_ADMIN);
    }

    public List<GrantedAuthority> authorities() {
        List<GrantedAuthority> list = new ArrayList<>();
        list.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        adminRoles.forEach(r -> list.add(new SimpleGrantedAuthority(r.authority())));
        return list;
    }
}
