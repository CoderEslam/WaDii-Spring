package com.doubleclick.wadii.order;

import java.util.EnumSet;
import java.util.Set;

/** Order lifecycle. Payment state is a separate domain and never stored here. */
public enum OrderStatus {
    CREATED,
    MATCHING,
    QUOTING,
    RESPONSES_AVAILABLE,
    ACCEPTED,
    IN_PROGRESS,
    COMPLETED,
    CANCELED,
    EXPIRED;

    /** Statuses in which the order still waits for offers and the response deadline applies. */
    public static final Set<OrderStatus> AWAITING_OFFERS = EnumSet.of(CREATED, MATCHING, QUOTING, RESPONSES_AVAILABLE);

    /** Providers may submit offers. */
    public boolean acceptsOffers() {
        return this == QUOTING || this == RESPONSES_AVAILABLE;
    }

    public boolean awaitingOffers() {
        return AWAITING_OFFERS.contains(this);
    }

    /** An accepted provider is assigned and work is not finished. */
    public boolean isAssigned() {
        return this == ACCEPTED || this == IN_PROGRESS;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELED || this == EXPIRED;
    }
}
