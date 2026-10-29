package com.doubleclick.wadii.identity;

/** Administrative permissions held by ADMIN accounts. SUPER_ADMIN implies every other one. */
public enum AdminRole {
    SUPER_ADMIN,
    SUPPORT_ADMIN,
    CONTENT_ADMIN,
    FINANCE_ADMIN;

    /** Spring authority name, e.g. {@code ADMIN_FINANCE}. */
    public String authority() {
        return "ADMIN_" + name().replace("_ADMIN", "");
    }
}
