package com.doubleclick.wadii.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** EGP amounts: always 2 decimals, HALF_UP. Never use floating point for money. */
public final class Money {

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private Money() {
    }

    public static BigDecimal of(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal percentOf(BigDecimal base, BigDecimal percent) {
        return of(base.multiply(percent).divide(HUNDRED, 4, RoundingMode.HALF_UP));
    }

    public static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}
