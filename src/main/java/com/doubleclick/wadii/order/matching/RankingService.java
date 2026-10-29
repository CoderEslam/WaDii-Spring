package com.doubleclick.wadii.order.matching;

import com.doubleclick.wadii.provider.Provider;
import com.doubleclick.wadii.provider.ProviderStats;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Provider ranking score (0..100). Internal: only used to order targets and the RECOMMENDED sort;
 * never returned to customers.
 * <pre>
 * 30% rating (Bayesian: blends towards 3.5 until ~20 ratings)
 * 25% reliability score (response, completion, cancellation, no-show, dispute rates)
 * 25% distance (1 at 0 km, 0 at the service radius; neutral 0.5 when unknown)
 * 10% completed orders (saturates at 50)
 * 10% response speed (1 under 15 min, 0 over 24 h; neutral 0.5 when unknown)
 * </pre>
 */
@Component
public class RankingService {

    private static final double PRIOR_RATING = 3.5;
    private static final double PRIOR_WEIGHT = 20;

    public BigDecimal score(Provider provider, ProviderStats stats, BigDecimal distanceKm, BigDecimal radiusKm) {
        double rating = bayesianRating(provider) / 5.0;
        double reliability = stats == null ? 0.5 : stats.getReliabilityScore().doubleValue() / 100.0;
        double distance = distanceKm == null || radiusKm == null || radiusKm.signum() == 0 ? 0.5
                : Math.max(0, 1 - distanceKm.doubleValue() / radiusKm.doubleValue());
        double completed = stats == null ? 0 : Math.min(stats.getCompletedCount(), 50) / 50.0;
        double speed = speed(stats);
        double score = 100 * (0.30 * rating + 0.25 * reliability + 0.25 * distance + 0.10 * completed + 0.10 * speed);
        return BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP);
    }

    public double bayesianRating(Provider provider) {
        double n = provider.getRatingCount();
        double avg = provider.getRatingAvg() == null ? 0 : provider.getRatingAvg().doubleValue();
        return (PRIOR_RATING * PRIOR_WEIGHT + avg * n) / (PRIOR_WEIGHT + n);
    }

    private double speed(ProviderStats stats) {
        if (stats == null || stats.getAvgResponseMinutes() == null) return 0.5;
        double minutes = stats.getAvgResponseMinutes().doubleValue();
        if (minutes <= 15) return 1;
        if (minutes >= 24 * 60) return 0;
        return 1 - (minutes - 15) / (24 * 60 - 15);
    }
}
