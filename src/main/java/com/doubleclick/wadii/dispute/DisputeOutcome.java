package com.doubleclick.wadii.dispute;

/** What the admin decided. Payment outcomes act on the disputed payment. */
public enum DisputeOutcome {
    /** Payment disputes: the money arrived; the payment becomes PROVIDER_CONFIRMED. */
    PAYMENT_CONFIRMED,
    /** Payment disputes: no money arrived; the payment becomes REJECTED. */
    PAYMENT_REJECTED,
    /** A refund of {@code refundAmount} is approved for the customer. */
    REFUND_CUSTOMER,
    /** Decided in favour of the provider, no financial action. */
    IN_FAVOUR_OF_PROVIDER,
    /** Decided in favour of the customer without a refund (e.g. rework). */
    IN_FAVOUR_OF_CUSTOMER,
    NO_ACTION
}
