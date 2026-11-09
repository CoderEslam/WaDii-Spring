package com.doubleclick.wadii.request;

import com.doubleclick.wadii.catalog.ServiceCategory;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.util.Geo;
import com.doubleclick.wadii.notification.NotificationService;
import com.doubleclick.wadii.notification.NotificationType;
import com.doubleclick.wadii.order.ProviderStats;
import com.doubleclick.wadii.provider.Provider;
import com.doubleclick.wadii.provider.ProviderBranch;
import com.doubleclick.wadii.provider.ProviderBranchRepository;
import com.doubleclick.wadii.provider.ProviderCategory;
import com.doubleclick.wadii.provider.ProviderCategoryRepository;
import com.doubleclick.wadii.provider.ProviderRepository;
import com.doubleclick.wadii.provider.VehicleSupport;
import com.doubleclick.wadii.provider.VehicleSupportRepository;
import com.doubleclick.wadii.settings.Configs.MarketplaceConfig;
import com.doubleclick.wadii.settings.PlatformConfigService;
import com.doubleclick.wadii.subscription.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Chooses which providers receive a request. A provider qualifies when it is verified, active, accepting orders,
 * not restricted by its subscription, has an active branch covering the location, and can serve at least one of
 * the requested items (offers the category and, where the category needs vehicle details, supports the vehicle).
 * Qualified providers are ordered by recent performance and proximity and capped by configuration.
 */
@Service
@RequiredArgsConstructor
public class MatchingService {

    private final ProviderRepository providers;
    private final ProviderCategoryRepository providerCategories;
    private final ProviderBranchRepository branches;
    private final VehicleSupportRepository vehicleSupport;
    private final RequestTargetRepository targets;
    private final SubscriptionService subscriptions;
    private final ProviderStats stats;
    private final PlatformConfigService config;
    private final NotificationService notifications;
    private final Clock clock;

    record Candidate(Provider provider, ProviderBranch branch, Double distanceKm, double score) {
    }

    /** Invites the matching providers; returns how many were invited. */
    @Transactional
    public int match(ServiceRequest request, List<ServiceItem> items, Map<Long, ServiceCategory> categories) {
        MarketplaceConfig cfg = config.marketplace();
        List<Candidate> candidates = eligible(request, items, categories, cfg);
        if (request.getExplicitProviderId() != null) {
            candidates = candidates.stream().filter(c -> c.provider().getId().equals(request.getExplicitProviderId())).toList();
            if (candidates.isEmpty()) {
                throw BusinessException.of(ErrorCode.PROVIDER_NOT_ELIGIBLE);
            }
        }
        List<Candidate> chosen = candidates.stream()
                .sorted(Comparator.comparingDouble(Candidate::score).reversed())
                .limit(cfg.maxProviderTargets())
                .toList();
        for (Candidate c : chosen) {
            RequestTarget t = new RequestTarget();
            t.setRequestId(request.getId());
            t.setProviderId(c.provider().getId());
            t.setBranchId(c.branch().getId());
            t.setDistanceKm(c.distanceKm() == null ? null : BigDecimal.valueOf(c.distanceKm()).setScale(2, RoundingMode.HALF_UP));
            t.setRankScore(BigDecimal.valueOf(c.score()).setScale(4, RoundingMode.HALF_UP));
            targets.save(t);
            notifications.notify(c.provider().getUserId(), NotificationType.NEW_MATCHING_REQUEST, "New service request",
                    "A customer near you needs " + categoryNames(items, categories, c.provider().getId()) + ".",
                    "REQUEST", request.getId());
        }
        return chosen.size();
    }

    private List<Candidate> eligible(ServiceRequest request, List<ServiceItem> items, Map<Long, ServiceCategory> categories,
                                     MarketplaceConfig cfg) {
        Set<Long> categoryIds = items.stream().map(ServiceItem::getCategoryId).collect(Collectors.toSet());
        List<Provider> base = providers.matchingCandidates(categoryIds).stream()
                .filter(p -> !p.getUserId().equals(request.getCustomerId()))
                .toList();
        if (base.isEmpty()) {
            return List.of();
        }
        List<Long> ids = base.stream().map(Provider::getId).toList();
        Map<Long, Boolean> restricted = subscriptions.restrictedByProvider(ids);
        Map<Long, List<ProviderBranch>> branchesByProvider = branches.findByProviderIdInAndActiveTrue(ids).stream()
                .collect(Collectors.groupingBy(ProviderBranch::getProviderId));
        Map<Long, List<VehicleSupport>> supportByProvider = vehicleSupport.findByProviderIdIn(ids).stream()
                .collect(Collectors.groupingBy(VehicleSupport::getProviderId));
        Map<Long, Double> scores = stats.rankScores(ids, Instant.now(clock).minus(Duration.ofDays(cfg.rankingWindowDays())));

        List<Candidate> result = new ArrayList<>();
        for (Provider p : base) {
            if (restricted.getOrDefault(p.getId(), false)) continue;
            if (!servesAnyItem(p.getId(), request, items, categories, supportByProvider.getOrDefault(p.getId(), List.of()))) continue;
            Candidate located = nearestCoveringBranch(p, request, branchesByProvider.getOrDefault(p.getId(), List.of()), cfg,
                    scores.getOrDefault(p.getId(), 0.5));
            if (located != null) result.add(located);
        }
        return result;
    }

    private boolean servesAnyItem(Long providerId, ServiceRequest request, List<ServiceItem> items,
                                  Map<Long, ServiceCategory> categories, List<VehicleSupport> support) {
        Set<Long> offered = providerCategories.findByProviderId(providerId).stream()
                .map(ProviderCategory::getCategoryId).collect(Collectors.toSet());
        for (ServiceItem item : items) {
            if (!item.isOpen() || !offered.contains(item.getCategoryId())) continue;
            ServiceCategory category = categories.get(item.getCategoryId());
            if (!category.isVehicleRequired() || !request.hasVehicle() || support.isEmpty()) return true;
            boolean covered = support.stream().anyMatch(s -> s.covers(request.getVehicleMake(), request.getVehicleModel(), request.getVehicleYear()));
            if (covered) return true;
        }
        return false;
    }

    /** Distance rule when both sides have coordinates; otherwise the branch must be in the same governorate. */
    private Candidate nearestCoveringBranch(Provider p, ServiceRequest request, List<ProviderBranch> list,
                                            MarketplaceConfig cfg, double performance) {
        Candidate best = null;
        for (ProviderBranch b : list) {
            Double distance = Geo.distanceKm(request.getLatitude(), request.getLongitude(), b.getLatitude(), b.getLongitude());
            double radius = b.getServiceRadiusKm() != null ? b.getServiceRadiusKm() : cfg.defaultMatchRadiusKm();
            boolean covers = distance != null ? distance <= radius : b.getAreaId().equals(request.getAreaId());
            if (!covers) continue;
            double proximity = distance == null ? 0.5 : 1.0 - Math.min(distance / radius, 1.0);
            double score = performance + 0.15 * proximity;
            if (best == null || (distance != null && (best.distanceKm() == null || distance < best.distanceKm()))) {
                best = new Candidate(p, b, distance, score);
            }
        }
        return best;
    }

    private String categoryNames(List<ServiceItem> items, Map<Long, ServiceCategory> categories, Long providerId) {
        Set<Long> offered = providerCategories.findByProviderId(providerId).stream()
                .map(ProviderCategory::getCategoryId).collect(Collectors.toSet());
        return items.stream().filter(i -> offered.contains(i.getCategoryId()))
                .map(i -> categories.get(i.getCategoryId()).getNameEn().toLowerCase())
                .collect(Collectors.joining(", "));
    }
}
