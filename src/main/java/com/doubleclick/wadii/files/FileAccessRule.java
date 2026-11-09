package com.doubleclick.wadii.files;

import com.doubleclick.wadii.common.security.AuthUser;

/**
 * Implemented by each module that attaches files to its records. Decides whether a user may read files attached
 * to one of its records, so the file module does not depend on every other module.
 */
public interface FileAccessRule {

    /** The {@code ref_type} values this rule answers for, e.g. {@code "DISPUTE"}. */
    boolean supports(String refType);

    boolean canRead(AuthUser user, String refType, Long refId);
}
