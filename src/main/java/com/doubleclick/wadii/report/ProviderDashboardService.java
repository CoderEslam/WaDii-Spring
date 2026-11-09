package com.doubleclick.wadii.report;

import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.common.util.Money;
import com.doubleclick.wadii.dispute.DisputeRepository;
import com.doubleclick.wadii.ledger.LedgerEntry;
import com.doubleclick.wadii.ledger.LedgerRepository;
import com.doubleclick.wadii.offer.OfferLineRepository;
import com.doubleclick.wadii.offer.OfferRepository;
import com.doubleclick.wadii.order.ProviderStats;
import com.doubleclick.wadii.order.RatingRepository;
import com.doubleclick.wadii.order.ServiceOrder;
import com.doubleclick.wadii.order.ServiceOrderRepository;
import com.doubleclick.wadii.provider.Provider;
import com.doubleclick.wadii.provider.ProviderAccess;
import com.doubleclick.wadii.request.RequestTarget;
import com.doubleclick.wadii.request.RequestTargetRepository;
import com.doubleclick.wadii.subscription.SubscriptionService;
import com.doubleclick.wadii.warranty.WarrantyClaimRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Provider dashboard. Cash figures are what the provider reported and the customer confirmed; WaDii does not
 * verify them, and the labels returned to clients must say so.
 */
@Service
@RequiredArgsConstructor
public class ProviderDashboardService {

    private static final Set<ServiceOrder.Status> ACTIVE = EnumSet.of(ServiceOrder.Status.ACCEPTED, ServiceOrder.Status.IN_PROGRESS,
            ServiceOrder.Status.PROVIDER_MARKED_COMPLETED, ServiceOrder.Status.INTERRUPTION_REQUESTED,
            ServiceOrder.Status.INTERRUPTION_STATEMENT_SUBMITTED, ServiceOrder.Status.DISPUTED);

    private final ProviderAccess access;
    private final RequestTargetRepository targets;
    private final OfferRepository offers;
    private final OfferLineRepository lines;
    private final ServiceOrderRepository orders;
    private final RatingRepository ratings;
    private final DisputeRepository disputes;
    private final WarrantyClaimRepository claims;
    private final LedgerRepository ledger;
    private final ProviderStats stats;
    private final SubscriptionService subscriptions;
    private final Clock clock;

    public record Operations(long invited, long viewed, long offersSubmitted, double responseRate, Long avgResponseMinutes,
                             long offersAccepted, long completedOrders, long providerCancellations, long customerCancellations,
                             long noShows, long activeOrders) {
    }

    public record Quality(BigDecimal ratingAverage, long ratingCount, BigDecimal recentRatingAverage, long disputes,
                          double disputeRate, long warrantyClaims, double completionRate) {
    }

    public record Finance(BigDecimal grossAccepted, BigDecimal grossCompleted, BigDecimal cashReportedCollected,
                          BigDecimal cashConfirmed, BigDecimal cashDisputed, BigDecimal commissionAccrued,
                          BigDecimal commissionSettled, BigDecimal outstandingBalance) {
    }

    public record Dashboard(int windowDays, Operations operations, Quality quality, Finance finance,
                            SubscriptionService.View subscription) {
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(AuthUser user, int days) {
        Provider p = access.operating(user);
        int window = days == 90 ? 90 : 30;
        Instant since = Instant.now(clock).minus(Duration.ofDays(window));
        Long id = p.getId();

        List<RequestTarget> responded = targets.responded(id, since);
        long invited = targets.countInvited(id, since);
        long offered = responded.stream().filter(t -> t.getStatus() == RequestTarget.Status.OFFERED).count();
        Long avgMinutes = responded.isEmpty() ? null : Math.round(responded.stream()
                .mapToLong(t -> Duration.between(t.getCreatedAt(), t.getRespondedAt()).toMinutes()).average().orElse(0));

        List<ServiceOrder> recent = orders.forProviderSince(id, since);
        long completed = count(recent, o -> o.getStatus() == ServiceOrder.Status.COMPLETED);
        long providerCancel = count(recent, o -> o.getCancellationKind() == ServiceOrder.CancellationKind.PROVIDER_CANCELED);
        long customerCancel = count(recent, o -> o.getCancellationKind() == ServiceOrder.CancellationKind.CUSTOMER_CANCELED);
        long noShows = count(recent, o -> o.getCancellationKind() == ServiceOrder.CancellationKind.PROVIDER_NO_SHOW
                || o.getCancellationKind() == ServiceOrder.CancellationKind.CUSTOMER_NO_SHOW);
        long active = count(orders.forProviderSince(id, Instant.EPOCH), o -> ACTIVE.contains(o.getStatus()));
        long providerFault = count(recent, o -> o.getCancellationKind() == ServiceOrder.CancellationKind.PROVIDER_CANCELED
                || o.getCancellationKind() == ServiceOrder.CancellationKind.PROVIDER_NO_SHOW);

        var allTime = stats.summary(id);
        BigDecimal recentAvg = ratings.summary(List.of(id), since).stream().findFirst()
                .map(r -> BigDecimal.valueOf(((Number) r[1]).doubleValue()).setScale(2, RoundingMode.HALF_UP)).orElse(null);
        long disputeCount = disputes.countForProvider(id, since);

        Set<LedgerEntry.Status> accruedStatuses = EnumSet.of(LedgerEntry.Status.DUE, LedgerEntry.Status.REPORTED_PAID, LedgerEntry.Status.SETTLED);
        Finance finance = new Finance(
                sum(recent, o -> true, ServiceOrder::getCustomerTotal),
                sum(recent, o -> o.getStatus() == ServiceOrder.Status.COMPLETED, ServiceOrder::amountDue),
                sum(recent, o -> o.getCashReportedAmount() != null, ServiceOrder::getCashReportedAmount),
                sum(recent, o -> o.getCashStatus() == ServiceOrder.CashStatus.CONFIRMED, ServiceOrder::getCashReportedAmount),
                sum(recent, o -> o.getCashStatus() == ServiceOrder.CashStatus.DISPUTED, ServiceOrder::getCashReportedAmount),
                Money.of(ledger.sumForProvider(id, LedgerEntry.Type.COMMISSION, accruedStatuses, since)),
                Money.of(ledger.sumForProvider(id, LedgerEntry.Type.COMMISSION, EnumSet.of(LedgerEntry.Status.SETTLED), since)),
                Money.of(ledger.sumForPayer(p.getUserId(), EnumSet.allOf(LedgerEntry.Type.class),
                        EnumSet.of(LedgerEntry.Status.DUE, LedgerEntry.Status.REPORTED_PAID))));

        return new Dashboard(window,
                new Operations(invited, targets.countViewed(id, since), offers.countSubmitted(id, since), ratio(offered, invited),
                        avgMinutes, lines.countAccepted(id, since), completed, providerCancel, customerCancel, noShows, active),
                new Quality(allTime.ratingAverage(), allTime.ratingCount(), recentAvg, disputeCount, ratio(disputeCount, recent.size()),
                        claims.countByProviderIdAndCreatedAtAfter(id, since), ratio(completed, completed + providerFault)),
                finance, subscriptions.view(id));
    }

    private static long count(List<ServiceOrder> list, Predicate<ServiceOrder> filter) {
        return list.stream().filter(filter).count();
    }

    private static BigDecimal sum(List<ServiceOrder> list, Predicate<ServiceOrder> filter,
                                  java.util.function.Function<ServiceOrder, BigDecimal> value) {
        return list.stream().filter(filter).map(value).filter(Objects::nonNull).reduce(Money.ZERO, BigDecimal::add);
    }

    private static double ratio(long part, long whole) {
        return whole == 0 ? 0 : Math.round((double) part / whole * 1000) / 1000.0;
    }
}
