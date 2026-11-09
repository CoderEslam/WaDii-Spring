package com.doubleclick.wadii.order;

import com.doubleclick.wadii.request.RequestTarget;
import com.doubleclick.wadii.request.RequestTargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provider reputation and recent performance. Ranking uses a rolling window so a provider with many old jobs does
 * not automatically outrank a newer provider whose recent service is better. Ranking only orders invitations; it
 * never sets prices and is not a quality guarantee.
 */
@Component
@RequiredArgsConstructor
public class ProviderStats {

    /** Used when a provider has no data yet, so newcomers are neither punished nor favored. */
    private static final double NEUTRAL = 0.5;

    static final Set<ServiceOrder.CancellationKind> PROVIDER_FAULT =
            EnumSet.of(ServiceOrder.CancellationKind.PROVIDER_CANCELED, ServiceOrder.CancellationKind.PROVIDER_NO_SHOW);

    private final RatingRepository ratings;
    private final ServiceOrderRepository orders;
    private final RequestTargetRepository targets;

    public record Summary(BigDecimal ratingAverage, long ratingCount, long completedOrders) {
        public static final Summary EMPTY = new Summary(BigDecimal.ZERO, 0, 0);
    }

    /** All-time visible rating average/count and completed orders, per provider. */
    @Transactional(readOnly = true)
    public Map<Long, Summary> summaries(Collection<Long> providerIds) {
        Map<Long, Summary> result = new HashMap<>();
        if (providerIds.isEmpty()) {
            return result;
        }
        Map<Long, Long> completed = new HashMap<>();
        for (Object[] row : orders.completedByProvider(providerIds)) {
            completed.put((Long) row[0], (Long) row[1]);
        }
        Map<Long, Object[]> rated = new HashMap<>();
        for (Object[] row : ratings.summary(providerIds, Instant.EPOCH)) {
            rated.put((Long) row[0], row);
        }
        for (Long id : providerIds) {
            Object[] r = rated.get(id);
            BigDecimal avg = r == null ? BigDecimal.ZERO
                    : BigDecimal.valueOf(((Number) r[1]).doubleValue()).setScale(2, RoundingMode.HALF_UP);
            result.put(id, new Summary(avg, r == null ? 0 : (Long) r[2], completed.getOrDefault(id, 0L)));
        }
        return result;
    }

    public Summary summary(Long providerId) {
        return summaries(List.of(providerId)).getOrDefault(providerId, Summary.EMPTY);
    }

    /** Score in roughly [0,1] from the provider's activity since {@code since}; higher ranks first. */
    @Transactional(readOnly = true)
    public Map<Long, Double> rankScores(Collection<Long> providerIds, Instant since) {
        Map<Long, Double> recentRating = new HashMap<>();
        for (Object[] row : ratings.summary(providerIds, since)) {
            recentRating.put((Long) row[0], ((Number) row[1]).doubleValue() / 5.0);
        }
        Map<Long, List<ServiceOrder>> recentOrders = new HashMap<>();
        if (!providerIds.isEmpty()) {
            for (ServiceOrder o : orders.forProvidersSince(providerIds, since)) {
                recentOrders.computeIfAbsent(o.getProviderId(), k -> new java.util.ArrayList<>()).add(o);
            }
        }
        Map<Long, Double> scores = new HashMap<>();
        for (Long id : providerIds) {
            long invited = targets.countInvited(id, since);
            long responded = targets.responded(id, since).stream().filter(t -> t.getStatus() == RequestTarget.Status.OFFERED).count();
            double responseRate = invited == 0 ? NEUTRAL : (double) responded / invited;
            List<ServiceOrder> list = recentOrders.getOrDefault(id, List.of());
            long completed = list.stream().filter(o -> o.getStatus() == ServiceOrder.Status.COMPLETED).count();
            long providerFault = list.stream().filter(o -> PROVIDER_FAULT.contains(o.getCancellationKind())).count();
            double completionRate = completed + providerFault == 0 ? NEUTRAL : (double) completed / (completed + providerFault);
            double cancelRate = list.isEmpty() ? 0 : (double) providerFault / list.size();
            double rating = recentRating.getOrDefault(id, NEUTRAL);
            scores.put(id, 0.35 * rating + 0.25 * responseRate + 0.25 * completionRate - 0.15 * cancelRate);
        }
        return scores;
    }
}
