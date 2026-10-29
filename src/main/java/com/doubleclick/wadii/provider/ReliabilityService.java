package com.doubleclick.wadii.provider;

import com.doubleclick.wadii.audit.FraudFlagType;
import com.doubleclick.wadii.audit.FraudService;
import com.doubleclick.wadii.common.config.WadiiProperties;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Recomputes provider reliability and customer behaviour statistics from the transactional tables.
 * <pre>
 * response rate    = responded / targeted          acceptance rate  = accepted / responded
 * completion rate  = completed / accepted          cancellation rate = provider cancellations / accepted
 * no-show rate     = provider no-shows / accepted  dispute rate     = disputes / accepted
 * reliability (0-100) = 25% response + 30% completion + 15% (1-cancellation) + 15% (1-no-show) + 15% (1-dispute),
 *                       blended towards 50 until the provider has about 10 accepted orders.
 * </pre>
 * Customer statistics are analytical only in V1; nothing restricts customers based on them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReliabilityService {

    private static final String PROVIDER_SQL = """
            SELECT p.id,
              (SELECT COUNT(*) FROM order_provider_targets t WHERE t.provider_id = p.id),
              (SELECT COUNT(*) FROM order_responses r WHERE r.provider_id = p.id),
              (SELECT COUNT(*) FROM order_responses r WHERE r.provider_id = p.id AND r.status = 'ACCEPTED'),
              (SELECT COUNT(*) FROM orders o WHERE o.accepted_provider_id = p.id AND o.status = 'COMPLETED'),
              (SELECT COUNT(*) FROM order_cancellations c JOIN orders o ON o.id = c.order_id
                 WHERE o.accepted_provider_id = p.id AND c.actor_role = 'PROVIDER'),
              (SELECT COUNT(*) FROM appointments a JOIN orders o ON o.id = a.order_id
                 WHERE o.accepted_provider_id = p.id AND a.status = 'PROVIDER_NO_SHOW'),
              (SELECT COUNT(*) FROM disputes d WHERE d.provider_id = p.id),
              (SELECT AVG(EXTRACT(EPOCH FROM (t.responded_at - t.notified_at)) / 60.0) FROM order_provider_targets t
                 WHERE t.provider_id = p.id AND t.responded_at IS NOT NULL AND t.notified_at IS NOT NULL),
              (SELECT COUNT(*) FROM order_cancellations c JOIN orders o ON o.id = c.order_id
                 WHERE o.accepted_provider_id = p.id AND c.actor_role = 'PROVIDER' AND c.created_at >= :since),
              p.user_id
            FROM providers p
            """;

    private static final String CUSTOMER_SQL = """
            SELECT u.id,
              (SELECT COUNT(*) FROM orders o WHERE o.customer_id = u.id),
              (SELECT COUNT(*) FROM orders o WHERE o.customer_id = u.id AND o.status = 'COMPLETED'),
              (SELECT COUNT(*) FROM order_cancellations c JOIN orders o ON o.id = c.order_id
                 WHERE o.customer_id = u.id AND c.actor_role = 'CUSTOMER'),
              (SELECT COUNT(*) FROM appointments a JOIN orders o ON o.id = a.order_id
                 WHERE o.customer_id = u.id AND a.status = 'CUSTOMER_NO_SHOW'),
              (SELECT COUNT(*) FROM disputes d WHERE d.opened_by = u.id)
            FROM users u WHERE u.role <> 'ADMIN' AND EXISTS (SELECT 1 FROM orders o WHERE o.customer_id = u.id)
            """;

    private final EntityManager entityManager;
    private final ProviderStatsRepository providerStatsRepository;
    private final CustomerStatsRepository customerStatsRepository;
    private final FraudService fraudService;
    private final WadiiProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${wadii.jobs.stats-interval-ms:3600000}", initialDelay = 120000)
    @Transactional
    public void recompute() {
        Instant now = clock.instant();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(PROVIDER_SQL)
                .setParameter("since", now.minus(Duration.ofDays(30)))
                .getResultList();
        for (Object[] row : rows) {
            Long providerId = ((Number) row[0]).longValue();
            ProviderStats s = providerStatsRepository.findById(providerId).orElseGet(() -> new ProviderStats(providerId));
            int targeted = n(row[1]), responded = n(row[2]), accepted = n(row[3]), completed = n(row[4]);
            int cancels = n(row[5]), noShows = n(row[6]), disputes = n(row[7]);
            s.setTargetedCount(targeted);
            s.setRespondedCount(responded);
            s.setAcceptedCount(accepted);
            s.setCompletedCount(completed);
            s.setProviderCancelCount(cancels);
            s.setNoShowCount(noShows);
            s.setDisputeCount(disputes);
            s.setAvgResponseMinutes(row[8] == null ? null : new BigDecimal(row[8].toString()).setScale(2, RoundingMode.HALF_UP));
            double responseRate = ratio(responded, targeted), completionRate = ratio(completed, accepted);
            double cancellationRate = ratio(cancels, accepted), noShowRate = ratio(noShows, accepted), disputeRate = ratio(disputes, accepted);
            s.setResponseRate(rate(responseRate));
            s.setAcceptanceRate(rate(ratio(accepted, responded)));
            s.setCompletionRate(rate(completionRate));
            s.setCancellationRate(rate(cancellationRate));
            s.setNoShowRate(rate(noShowRate));
            s.setDisputeRate(rate(disputeRate));
            double raw = 100 * (0.25 * responseRate + 0.30 * completionRate + 0.15 * (1 - cancellationRate)
                    + 0.15 * (1 - noShowRate) + 0.15 * (1 - disputeRate));
            double weight = Math.min(accepted + targeted / 2.0, 10) / 10.0;
            s.setReliabilityScore(BigDecimal.valueOf(50 * (1 - weight) + raw * weight).setScale(2, RoundingMode.HALF_UP));
            s.setUpdatedAt(now);
            providerStatsRepository.save(s);
            int recentCancels = n(row[9]);
            if (recentCancels > properties.getFraud().getMaxProviderCancellationsPer30Days()) {
                fraudService.flag(((Number) row[10]).longValue(), FraudFlagType.EXCESSIVE_PROVIDER_CANCELLATIONS, "PROVIDER", providerId,
                        recentCancels + " provider cancellations in 30 days");
            }
        }
        @SuppressWarnings("unchecked")
        List<Object[]> customers = entityManager.createNativeQuery(CUSTOMER_SQL).getResultList();
        for (Object[] row : customers) {
            Long userId = ((Number) row[0]).longValue();
            CustomerStats c = customerStatsRepository.findById(userId).orElseGet(() -> new CustomerStats(userId));
            c.setOrdersCount(n(row[1]));
            c.setCompletedCount(n(row[2]));
            c.setCanceledCount(n(row[3]));
            c.setNoShowCount(n(row[4]));
            c.setDisputeCount(n(row[5]));
            c.setUpdatedAt(now);
            customerStatsRepository.save(c);
        }
        log.info("Reliability statistics recomputed for {} providers and {} customers", rows.size(), customers.size());
    }

    private static int n(Object o) {
        return o == null ? 0 : ((Number) o).intValue();
    }

    private static double ratio(int a, int b) {
        return b == 0 ? 0 : Math.min(1.0, (double) a / b);
    }

    private static BigDecimal rate(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }
}
