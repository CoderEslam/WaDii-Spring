package com.doubleclick.wadii.order.matching;

import com.doubleclick.wadii.common.config.WadiiProperties;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.geo.Geo;
import com.doubleclick.wadii.notification.NotificationService;
import com.doubleclick.wadii.notification.NotificationType;
import com.doubleclick.wadii.order.Order;
import com.doubleclick.wadii.order.OrderProviderTarget;
import com.doubleclick.wadii.order.OrderProviderTargetRepository;
import com.doubleclick.wadii.order.OrderTimelineService;
import com.doubleclick.wadii.order.TimelineEvent;
import com.doubleclick.wadii.provider.Provider;
import com.doubleclick.wadii.provider.ProviderBranch;
import com.doubleclick.wadii.provider.ProviderRepository;
import com.doubleclick.wadii.provider.ProviderStats;
import com.doubleclick.wadii.provider.ProviderStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Decides which providers receive an order.
 * <ul>
 *   <li>Explicit provider: only that provider, after checking it is active, verified, accepting orders and offers
 *       every requested service.</li>
 *   <li>Automatic: eligible providers offering ALL requested services, with an active branch inside their service
 *       radius of the order location (when the order has one) that opens on the requested day, ranked and capped
 *       at {@code wadii.order.max-targets}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MatchingService {

    private final ProviderRepository providerRepository;
    private final ProviderStatsRepository statsRepository;
    private final OrderProviderTargetRepository targetRepository;
    private final RankingService rankingService;
    private final NotificationService notificationService;
    private final OrderTimelineService timeline;
    private final WadiiProperties properties;
    private final Clock clock;

    public record Candidate(Provider provider, ProviderBranch branch, BigDecimal distanceKm, BigDecimal score) {
    }

    public Provider validateExplicit(Long providerId, Long customerId, Set<Long> serviceIds) {
        Provider p = providerRepository.findById(providerId).orElseThrow(() -> BusinessException.notFound(ErrorCode.PROVIDER_NOT_FOUND));
        if (p.isOwnedBy(customerId)) {
            throw BusinessException.of(ErrorCode.SELF_ORDER_FORBIDDEN);
        }
        if (!p.isEligibleForOrders()) {
            throw BusinessException.of(ErrorCode.PROVIDER_NOT_ELIGIBLE);
        }
        if (!p.offersAll(serviceIds)) {
            throw BusinessException.of(ErrorCode.PROVIDER_SERVICES_MISMATCH);
        }
        return p;
    }

    /** Ranked candidates for automatic matching, excluding providers already targeted. */
    @Transactional(readOnly = true)
    public List<Candidate> candidates(Order order) {
        Set<Long> serviceIds = order.serviceIds();
        List<Provider> eligible = providerRepository.findEligibleOfferingAll(serviceIds, serviceIds.size(), order.getCustomer().getId());
        if (eligible.isEmpty()) return List.of();
        Map<Long, ProviderStats> stats = statsRepository.findByProviderIdIn(eligible.stream().map(Provider::getId).toList())
                .stream().collect(Collectors.toMap(ProviderStats::getProviderId, Function.identity()));
        DayOfWeek day = order.getRequestedDate().getDayOfWeek();
        List<Candidate> result = new ArrayList<>();
        for (Provider p : eligible) {
            if (targetRepository.existsByOrderIdAndProviderId(order.getId(), p.getId())) continue;
            BigDecimal radius = p.getServiceRadiusKm() != null ? p.getServiceRadiusKm() : properties.getOrder().getDefaultServiceRadiusKm();
            Optional<Candidate> best = p.getBranches().stream()
                    .filter(ProviderBranch::isActive)
                    .filter(b -> b.opensOn(day))
                    .map(b -> {
                        BigDecimal d = order.hasLocation() && b.hasLocation()
                                ? Geo.distanceKm(order.getLatitude(), order.getLongitude(), b.getLatitude(), b.getLongitude()) : null;
                        return new Candidate(p, b, d, null);
                    })
                    .filter(c -> !order.hasLocation() || (c.distanceKm() != null && c.distanceKm().compareTo(radius) <= 0))
                    .min(Comparator.comparing(c -> c.distanceKm() == null ? BigDecimal.valueOf(Long.MAX_VALUE) : c.distanceKm()));
            best.ifPresent(c -> result.add(new Candidate(p, c.branch(), c.distanceKm(),
                    rankingService.score(p, stats.get(p.getId()), c.distanceKm(), radius))));
        }
        result.sort(Comparator.comparing(Candidate::score).reversed().thenComparing(c -> c.provider().getId()));
        return result.size() > properties.getOrder().getMaxTargets() ? result.subList(0, properties.getOrder().getMaxTargets()) : result;
    }

    /** Creates target rows and notifies each provider (after commit). Returns how many were targeted. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int target(Order order, List<Candidate> candidates) {
        Instant now = clock.instant();
        int count = 0;
        for (Candidate c : candidates) {
            OrderProviderTarget t = new OrderProviderTarget();
            t.setOrder(order);
            t.setProvider(c.provider());
            t.setBranchId(c.branch() == null ? null : c.branch().getId());
            t.setDistanceKm(c.distanceKm());
            t.setRankScore(c.score());
            t.setNotifiedAt(now);
            targetRepository.save(t);
            // No customer identity in the notification, only the order reference.
            notificationService.notify(c.provider().getUser().getId(), NotificationType.ORDER_TARGETED, "New order request",
                    "Order #" + order.getId() + " matches your services. Send your offer before it expires.", order.getId());
            count++;
        }
        if (count > 0) {
            timeline.system(order.getId(), TimelineEvent.PROVIDERS_NOTIFIED, count + " provider(s) notified");
        }
        return count;
    }

    /** Nearest active branch of an explicitly selected provider (for distance display). */
    public Candidate explicitCandidate(Order order, Provider provider) {
        ProviderBranch nearest = null;
        BigDecimal distance = null;
        for (ProviderBranch b : provider.getBranches()) {
            if (!b.isActive()) continue;
            BigDecimal d = order.hasLocation() && b.hasLocation()
                    ? Geo.distanceKm(order.getLatitude(), order.getLongitude(), b.getLatitude(), b.getLongitude()) : null;
            if (nearest == null || (d != null && (distance == null || d.compareTo(distance) < 0))) {
                nearest = b;
                distance = d;
            }
        }
        return new Candidate(provider, nearest, distance, null);
    }
}
