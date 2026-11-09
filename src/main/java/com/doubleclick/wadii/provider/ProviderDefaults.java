package com.doubleclick.wadii.provider;

import com.doubleclick.wadii.provider.ProviderViews.PolicyInput;

/** Defaults applied while a provider has not written their own terms. */
public final class ProviderDefaults {

    /** Customer-facing terms used until the provider saves a policy; they never remove mandatory legal rights. */
    public static final PolicyInput POLICY = new PolicyInput(
            "Free cancellation before the service starts. Once work has started, stopping it follows the interruption process.",
            "Each party should arrive within the platform grace period. A no-show cancels the order without a fee in this version.",
            "Work is performed as described in the accepted offer. Additional work requires the customer's approval first.",
            "Returns and refunds of parts follow Egyptian consumer protection law.");

    private ProviderDefaults() {
    }
}
