package com.doubleclick.wadii.dispute;

public enum DisputeStatus {
    OPEN,
    UNDER_REVIEW,
    WAITING_CUSTOMER,
    WAITING_PROVIDER,
    RESOLVED,
    REJECTED,
    CANCELED;

    public boolean isOpen() {
        return this != RESOLVED && this != REJECTED && this != CANCELED;
    }
}
