package com.doubleclick.wadii.common.security;

import com.doubleclick.wadii.identity.AdminRole;
import com.doubleclick.wadii.identity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The authenticated caller, rebuilt from the database on every request. Services take this instead of any
 * client-supplied user id. Provider capability is not part of it: provider endpoints resolve the caller's
 * provider profile through {@code ProviderAccess}, so a customer and a provider are the same account.
 */
public record AuthUser(Long id, String email, String fullName, boolean admin, Set<AdminRole> adminRoles) {

    public static AuthUser of(User user) {
        return new AuthUser(user.getId(), user.getEmail(), user.getFullName(), user.isAdmin(), user.effectiveAdminRoles());
    }

    public boolean hasAdminRole(AdminRole role) {
        return admin && adminRoles.contains(role);
    }

    public boolean isSupport() {
        return hasAdminRole(AdminRole.SUPPORT_ADMIN);
    }

    public boolean isFinance() {
        return hasAdminRole(AdminRole.FINANCE_ADMIN);
    }

    public List<GrantedAuthority> authorities() {
        List<GrantedAuthority> list = new ArrayList<>();
        list.add(new SimpleGrantedAuthority(admin ? "ROLE_ADMIN" : "ROLE_USER"));
        adminRoles.forEach(r -> list.add(new SimpleGrantedAuthority(r.authority())));
        return list;
    }
}
