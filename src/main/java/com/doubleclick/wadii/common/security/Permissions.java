package com.doubleclick.wadii.common.security;

/** SpEL expressions for {@code @PreAuthorize}. SUPER_ADMIN holds every ADMIN_* authority. */
public final class Permissions {

    public static final String AUTHENTICATED = "isAuthenticated()";
    public static final String PROVIDER = "hasRole('PROVIDER')";
    /** Customers: any non-admin account (a provider may also order services for their own car). */
    public static final String CUSTOMER = "hasAnyRole('USER','PROVIDER')";
    public static final String ADMIN = "hasRole('ADMIN')";
    public static final String SUPER_ADMIN = "hasAuthority('ADMIN_SUPER')";
    public static final String SUPPORT_ADMIN = "hasAuthority('ADMIN_SUPPORT')";
    public static final String CONTENT_ADMIN = "hasAuthority('ADMIN_CONTENT')";
    public static final String FINANCE_ADMIN = "hasAuthority('ADMIN_FINANCE')";
    public static final String SUPPORT_OR_FINANCE = "hasAnyAuthority('ADMIN_SUPPORT','ADMIN_FINANCE')";

    private Permissions() {
    }
}
