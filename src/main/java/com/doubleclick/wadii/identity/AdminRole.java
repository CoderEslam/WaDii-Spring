package com.doubleclick.wadii.identity;

/** Staff sub-roles. SUPER_ADMIN implicitly holds every other role. */
public enum AdminRole {
    SUPER_ADMIN("ADMIN_SUPER"),
    SUPPORT_ADMIN("ADMIN_SUPPORT"),
    FINANCE_ADMIN("ADMIN_FINANCE"),
    CONTENT_ADMIN("ADMIN_CONTENT");

    private final String authority;

    AdminRole(String authority) {
        this.authority = authority;
    }

    public String authority() {
        return authority;
    }
}
