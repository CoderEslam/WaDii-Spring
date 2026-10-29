package com.doubleclick.wadii.policy;

/** Where a snapshot came from. Precedence: legal/platform rules, then the provider policy (product-level reserved). */
public enum PolicySource {
    PLATFORM,
    PROVIDER
}
