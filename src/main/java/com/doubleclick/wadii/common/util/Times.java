package com.doubleclick.wadii.common.util;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Platform calendar: instants are UTC, local dates and working hours are Africa/Cairo. */
public final class Times {

    public static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");

    private Times() {
    }

    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(CAIRO));
    }

    public static ZonedDateTime local(Instant instant) {
        return instant.atZone(CAIRO);
    }
}
