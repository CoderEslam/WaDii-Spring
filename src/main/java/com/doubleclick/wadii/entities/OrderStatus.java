package com.doubleclick.wadii.entities;

public enum OrderStatus {
    PENDING,
    RESPONSES_AVAILABLE,
    ACCEPTED,
    IN_PROGRESS,
    COMPLETED,
    CANCELED;

    /** An order is open while providers may still respond and the customer may still choose. */
    public boolean isOpen() {
        return this == PENDING || this == RESPONSES_AVAILABLE;
    }
}
