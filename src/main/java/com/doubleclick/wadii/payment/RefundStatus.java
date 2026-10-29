package com.doubleclick.wadii.payment;

public enum RefundStatus {
    REQUESTED,
    APPROVED,
    REJECTED,
    PROCESSING,
    COMPLETED,
    FAILED;

    /** Counts against the refundable amount while in one of these. */
    public boolean isCommitted() {
        return this == REQUESTED || this == APPROVED || this == PROCESSING || this == COMPLETED;
    }
}
