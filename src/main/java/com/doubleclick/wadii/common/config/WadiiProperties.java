package com.doubleclick.wadii.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.ZoneId;

/** Every configurable business rule, bound from {@code wadii.*}. Defaults match application.properties. */
@Getter
@Setter
@ConfigurationProperties(prefix = "wadii")
public class WadiiProperties {

    private String timezone = "Africa/Cairo";
    private String currency = "EGP";
    private final Storage storage = new Storage();
    private final Fcm fcm = new Fcm();
    private final OrderRules order = new OrderRules();
    private final OfferRules offer = new OfferRules();
    private final PaymentRules payment = new PaymentRules();
    private final NoShow noShow = new NoShow();
    private final Commission commission = new Commission();
    private final PlatformPolicy platformPolicy = new PlatformPolicy();
    private final Fraud fraud = new Fraud();

    public ZoneId zone() {
        return ZoneId.of(timezone);
    }

    @Getter
    @Setter
    public static class Storage {
        private String root = "uploads";
    }

    @Getter
    @Setter
    public static class Fcm {
        private boolean enabled = true;
        private String projectId = "wadii-kmp";
        private String credentialsPath = "src/main/wadii.json";
    }

    @Getter
    @Setter
    public static class OrderRules {
        private int responseWindowHours = 48;
        private int maxTargets = 20;
        private BigDecimal defaultServiceRadiusKm = BigDecimal.valueOf(50);
        /** 0 disables auto-completion. */
        private int autoCompleteHours = 72;
    }

    @Getter
    @Setter
    public static class OfferRules {
        private int defaultValidityHours = 48;
        private int minValidityHours = 1;
        private int maxValidityHours = 168;
    }

    @Getter
    @Setter
    public static class PaymentRules {
        private int pendingTtlHours = 72;
        private int maxProofsPerPayment = 5;
    }

    @Getter
    @Setter
    public static class NoShow {
        private boolean cancelOrder = true;
        private boolean refundOnProviderNoShow = true;
        private int graceMinutes = 30;
    }

    @Getter
    @Setter
    public static class Commission {
        private BigDecimal percent = BigDecimal.ZERO;
    }

    @Getter
    @Setter
    public static class PlatformPolicy {
        private BigDecimal maxCancellationFeePercent = BigDecimal.valueOf(50);
        private int minWarrantyDays = 0;
        private int minRefundWindowDays = 0;
        private BigDecimal maxNoShowFeePercent = BigDecimal.valueOf(50);
    }

    @Getter
    @Setter
    public static class Fraud {
        private int maxDisputesPer30Days = 5;
        private int maxProviderCancellationsPer30Days = 5;
        private int maxAuthFailuresPerHour = 20;
    }
}
