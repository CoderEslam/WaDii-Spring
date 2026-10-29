package com.doubleclick.wadii.payment;

public enum PaymentMethod {
    VODAFONE_CASH,
    INSTAPAY,
    CASH,
    CARD,
    WALLET,
    OTHER;

    /** Methods available in V1. The others are reserved for automated gateways. */
    public boolean isSupported() {
        return this == VODAFONE_CASH || this == INSTAPAY || this == CASH;
    }

    /** Manual transfers verified with an uploaded proof. */
    public boolean usesProof() {
        return this == VODAFONE_CASH || this == INSTAPAY;
    }
}
