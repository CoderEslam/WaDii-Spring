package com.doubleclick.wadii.report;

import com.doubleclick.wadii.catalog.CatalogService;
import com.doubleclick.wadii.common.util.Money;
import com.doubleclick.wadii.common.util.Times;
import com.doubleclick.wadii.dispute.Dispute;
import com.doubleclick.wadii.dispute.DisputeRepository;
import com.doubleclick.wadii.fraud.FraudFlag;
import com.doubleclick.wadii.fraud.FraudFlagRepository;
import com.doubleclick.wadii.identity.UserRepository;
import com.doubleclick.wadii.ledger.LedgerEntry;
import com.doubleclick.wadii.ledger.LedgerRepository;
import com.doubleclick.wadii.order.ServiceOrder;
import com.doubleclick.wadii.order.ServiceOrderRepository;
import com.doubleclick.wadii.provider.Provider;
import com.doubleclick.wadii.provider.ProviderRepository;
import com.doubleclick.wadii.request.ServiceRequestRepository;
import com.doubleclick.wadii.subscription.SubscriptionService;
import com.doubleclick.wadii.warranty.WarrantyClaim;
import com.doubleclick.wadii.warranty.WarrantyClaimRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Platform-wide summary for the admin dashboard over a local-date range (Africa/Cairo, inclusive). */
@Service
@RequiredArgsConstructor
public class AdminReportService {

    private static final Set<LedgerEntry.Status> OPEN = EnumSet.of(LedgerEntry.Status.DUE, LedgerEntry.Status.REPORTED_PAID);
    private static final Set<LedgerEntry.Status> ACCRUED = EnumSet.of(LedgerEntry.Status.DUE, LedgerEntry.Status.REPORTED_PAID,
            LedgerEntry.Status.SETTLED);
    private static final Set<LedgerEntry.Status> SETTLED = EnumSet.of(LedgerEntry.Status.SETTLED);

    private final ServiceRequestRepository requests;
    private final ServiceOrderRepository orders;
    private final UserRepository users;
    private final ProviderRepository providers;
    private final LedgerRepository ledger;
    private final DisputeRepository disputes;
    private final FraudFlagRepository flags;
    private final WarrantyClaimRepository claims;
    private final SubscriptionService subscriptions;
    private final CatalogService catalog;
    private final Clock clock;

    public record Counts(long total, Map<String, Long> byStatus) {
    }

    public record OrderCounts(long total, Map<String, Long> byStatus, Map<String, Long> byCategory) {
    }

    public record UserCounts(long total, long newInRange, long providersVerified, long pendingApplications) {
    }

    public record FinanceTotals(BigDecimal commissionAccrued, BigDecimal commissionSettled, BigDecimal subscriptionDue,
                                BigDecimal subscriptionSettled, BigDecimal sellerFeesDue, BigDecimal sellerFeesSettled,
                                long overdueProviders) {
    }

    public record TrustCounts(long openDisputes, long openFraudFlags, long openWarrantyClaims, long cancellations, long noShows) {
    }

    public record Summary(LocalDate from, LocalDate to, Counts requests, OrderCounts orders, UserCounts users,
                          FinanceTotals finance, TrustCounts trust) {
    }

    @Transactional(readOnly = true)
    public Summary summary(LocalDate fromDate, LocalDate toDate) {
        LocalDate to = toDate != null ? toDate : Times.today(clock);
        LocalDate from = fromDate != null ? fromDate : to.minusDays(29);
        Instant start = from.atStartOfDay(Times.CAIRO).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(Times.CAIRO).toInstant();

        Map<String, Long> requestStatus = group(requests.countByStatus(start, end));
        Map<String, Long> orderStatus = group(orders.countByStatus(start, end));
        Map<Long, String> codes = new LinkedHashMap<>();
        catalog.allCategories().forEach(c -> codes.put(c.id(), c.code()));
        Map<String, Long> byCategory = new LinkedHashMap<>();
        for (Object[] row : orders.countByCategory(start, end)) {
            byCategory.put(codes.get((Long) row[0]), (Long) row[1]);
        }
        long overdue = subscriptions.adminList(SubscriptionService.Status.PAYMENT_OVERDUE, 0, 1).totalItems()
                + subscriptions.adminList(SubscriptionService.Status.RESTRICTED, 0, 1).totalItems();

        return new Summary(from, to,
                new Counts(requestStatus.values().stream().mapToLong(Long::longValue).sum(), requestStatus),
                new OrderCounts(orderStatus.values().stream().mapToLong(Long::longValue).sum(), orderStatus, byCategory),
                new UserCounts(users.count(), users.countByCreatedAtBetween(start, end),
                        providers.countByApplicationStatus(Provider.ApplicationStatus.VERIFIED),
                        providers.countByApplicationStatusIn(EnumSet.of(Provider.ApplicationStatus.SUBMITTED, Provider.ApplicationStatus.UNDER_REVIEW))),
                new FinanceTotals(
                        sum(LedgerEntry.Type.COMMISSION, ACCRUED, start, end), sum(LedgerEntry.Type.COMMISSION, SETTLED, start, end),
                        sum(LedgerEntry.Type.SUBSCRIPTION, OPEN, start, end), sum(LedgerEntry.Type.SUBSCRIPTION, SETTLED, start, end),
                        sum(LedgerEntry.Type.SELLER_FEE, OPEN, start, end), sum(LedgerEntry.Type.SELLER_FEE, SETTLED, start, end),
                        overdue),
                new TrustCounts(disputes.countByStatusIn(EnumSet.of(Dispute.Status.OPEN, Dispute.Status.UNDER_REVIEW)),
                        flags.countByStatus(FraudFlag.Status.OPEN),
                        claims.countByStatusIn(EnumSet.of(WarrantyClaim.Status.OPEN, WarrantyClaim.Status.ESCALATED)),
                        orders.countCanceledInRange(EnumSet.of(ServiceOrder.CancellationKind.CUSTOMER_CANCELED,
                                ServiceOrder.CancellationKind.PROVIDER_CANCELED, ServiceOrder.CancellationKind.ADMIN_CANCELED), start, end),
                        orders.countCanceledInRange(EnumSet.of(ServiceOrder.CancellationKind.CUSTOMER_NO_SHOW,
                                ServiceOrder.CancellationKind.PROVIDER_NO_SHOW), start, end)));
    }

    private BigDecimal sum(LedgerEntry.Type type, Set<LedgerEntry.Status> statuses, Instant from, Instant to) {
        return Money.of(ledger.sumInRange(type, statuses, from, to));
    }

    private static Map<String, Long> group(List<Object[]> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        rows.forEach(r -> map.put(String.valueOf(r[0]), (Long) r[1]));
        return map;
    }
}
