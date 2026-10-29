package com.doubleclick.wadii.payment;

import java.util.EnumSet;
import java.util.Set;

public enum PaymentStatus {
    PENDING,
    PROOF_SUBMITTED,
    PROVIDER_CONFIRMED,
    REJECTED,
    DISPUTED,
    EXPIRED,
    CANCELED,
    REFUNDED;

    /** At most one payment per order can be in one of these (partial unique index). */
    public static final Set<PaymentStatus> ACTIVE = EnumSet.of(PENDING, PROOF_SUBMITTED, DISPUTED);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }
}
