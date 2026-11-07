package com.doubleclick.wadii.settings;

import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;

import java.math.BigDecimal;
import java.util.List;

/**
 * Typed payloads of {@link ConfigKey} groups. Each one validates itself before a new version is stored, so a
 * stored version is always usable by the services that read it.
 */
public final class Configs {

    private Configs() {
    }

    public interface Validated {
        void validate();
    }

    public enum FeeMode { NONE, FIXED, PERCENT }

    public enum CommissionPayer { CUSTOMER, PROVIDER }

    public enum FeeTiming { ON_PUBLISH, ON_SOLD }

    public record MarketplaceConfig(int responseDeadlineHours, int offerValidityDefaultHours, int offerValidityMinHours,
                                    int offerValidityMaxHours, int maxProviderTargets, int defaultMatchRadiusKm,
                                    int noShowGraceMinutes, int rankingWindowDays, int providerCancellationFlagThreshold,
                                    int customerCancellationFlagThreshold, int disputeFlagThreshold) implements Validated {
        @Override
        public void validate() {
            require(responseDeadlineHours >= 1 && responseDeadlineHours <= 336, "responseDeadlineHours must be 1..336");
            require(offerValidityMinHours >= 1, "offerValidityMinHours must be >= 1");
            require(offerValidityMaxHours >= offerValidityMinHours, "offerValidityMaxHours must be >= offerValidityMinHours");
            require(offerValidityDefaultHours >= offerValidityMinHours && offerValidityDefaultHours <= offerValidityMaxHours,
                    "offerValidityDefaultHours must be within min..max");
            require(maxProviderTargets >= 1 && maxProviderTargets <= 200, "maxProviderTargets must be 1..200");
            require(defaultMatchRadiusKm >= 1 && defaultMatchRadiusKm <= 500, "defaultMatchRadiusKm must be 1..500");
            require(noShowGraceMinutes >= 0 && noShowGraceMinutes <= 240, "noShowGraceMinutes must be 0..240");
            require(rankingWindowDays >= 7 && rankingWindowDays <= 365, "rankingWindowDays must be 7..365");
            require(providerCancellationFlagThreshold >= 1 && customerCancellationFlagThreshold >= 1 && disputeFlagThreshold >= 1,
                    "flag thresholds must be >= 1");
        }
    }

    /**
     * Platform commission per Service Order. The base is the provider total (service + labor + parts - discount),
     * never the commission itself. A fixed amount is charged once per Service Order.
     */
    public record CommissionConfig(FeeMode mode, BigDecimal value, CommissionPayer payer) implements Validated {
        @Override
        public void validate() {
            require(mode != null && payer != null, "mode and payer are required");
            require(value != null && value.signum() >= 0, "value must be >= 0");
            require(mode != FeeMode.PERCENT || value.compareTo(new BigDecimal("100")) <= 0, "percent must be <= 100");
        }
    }

    /** Monthly provider subscription. A price of zero means free: no billing and no restriction. */
    public record SubscriptionConfig(BigDecimal monthlyPrice, int graceDays, List<Integer> reminderDaysBefore) implements Validated {
        @Override
        public void validate() {
            require(monthlyPrice != null && monthlyPrice.signum() >= 0, "monthlyPrice must be >= 0");
            require(graceDays >= 0 && graceDays <= 60, "graceDays must be 0..60");
            require(reminderDaysBefore != null && reminderDaysBefore.stream().allMatch(d -> d != null && d >= 1 && d <= 30),
                    "reminderDaysBefore must contain values 1..30");
        }
    }

    /** Individual used-item seller limits and fee. The quota counts listings in review or active. */
    public record SellerConfig(int maxActiveListings, FeeMode feeMode, BigDecimal feeValue, FeeTiming feeTiming,
                               int listingDurationDays, boolean moderationRequired) implements Validated {
        @Override
        public void validate() {
            require(maxActiveListings >= 1 && maxActiveListings <= 100, "maxActiveListings must be 1..100");
            require(feeMode != null && feeTiming != null, "feeMode and feeTiming are required");
            require(feeValue != null && feeValue.signum() >= 0, "feeValue must be >= 0");
            require(feeMode != FeeMode.PERCENT || feeValue.compareTo(new BigDecimal("100")) <= 0, "percent must be <= 100");
            require(listingDurationDays >= 1 && listingDurationDays <= 365, "listingDurationDays must be 1..365");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw BusinessException.of(ErrorCode.CONFIG_INVALID, message);
        }
    }
}
