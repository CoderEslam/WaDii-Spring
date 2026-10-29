package com.doubleclick.wadii.appointment;

public enum AppointmentStatus {
    SCHEDULED,
    RESCHEDULED,
    COMPLETED,
    CANCELED,
    CUSTOMER_NO_SHOW,
    PROVIDER_NO_SHOW;

    public boolean isOpen() {
        return this == SCHEDULED || this == RESCHEDULED;
    }
}
