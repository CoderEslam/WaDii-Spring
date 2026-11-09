package com.doubleclick.wadii.ledger;

import com.doubleclick.wadii.audit.AuditService;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.common.util.Money;
import com.doubleclick.wadii.notification.NotificationService;
import com.doubleclick.wadii.notification.NotificationType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

/** Creates and moves ledger entries. Only finance admins can settle or waive; payers can only report payment. */
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerRepository repository;
    private final AuditService audit;
    private final NotificationService notifications;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Published after an entry becomes SETTLED; the subscription module renews the provider's cycle from it. */
    public record EntrySettled(Long entryId, LedgerEntry.Type type, Long providerId, LocalDate paidOn) {
    }

    public record View(Long id, LedgerEntry.Type type, LedgerEntry.Status status, BigDecimal amount, String currency,
                       Long payerUserId, Long providerId, Long orderId, Long requestId, Long listingId,
                       String ruleDescription, Long configVersionId, LocalDate dueDate, Instant reportedAt,
                       Instant settledAt, LedgerEntry.Method method, String reference, String note, Instant createdAt) {
        public static View of(LedgerEntry e) {
            return new View(e.getId(), e.getType(), e.getStatus(), e.getAmount(), e.getCurrency(), e.getPayerUserId(),
                    e.getProviderId(), e.getOrderId(), e.getRequestId(), e.getListingId(), e.getRuleDescription(),
                    e.getConfigVersionId(), e.getDueDate(), e.getReportedAt(), e.getSettledAt(), e.getMethod(),
                    e.getReference(), e.getNote(), e.getCreatedAt());
        }
    }

    public record ReportPaidRequest(@NotNull LedgerEntry.Method method, @Size(max = 120) String reference) {
    }

    public record SettleRequest(@NotNull LedgerEntry.Method method, @Size(max = 120) String reference,
                                @Size(max = 1000) String note) {
    }

    /** Fields describing what an entry is owed for. */
    public record Charge(LedgerEntry.Type type, BigDecimal amount, Long payerUserId, Long providerId, Long orderId,
                         Long requestId, Long listingId, String ruleDescription, Long configVersionId, LocalDate dueDate) {
    }

    /** Records an amount due. Zero amounts are not recorded: a free rule creates no receivable. */
    @Transactional
    public LedgerEntry due(Charge charge) {
        if (!Money.isPositive(charge.amount())) {
            return null;
        }
        LedgerEntry e = new LedgerEntry();
        e.setType(charge.type());
        e.setAmount(Money.of(charge.amount()));
        e.setPayerUserId(charge.payerUserId());
        e.setProviderId(charge.providerId());
        e.setOrderId(charge.orderId());
        e.setRequestId(charge.requestId());
        e.setListingId(charge.listingId());
        e.setRuleDescription(charge.ruleDescription());
        e.setConfigVersionId(charge.configVersionId());
        e.setDueDate(charge.dueDate());
        repository.save(e);
        audit.record("LEDGER_DUE", "LEDGER", e.getId(), charge.type() + " " + e.getAmount());
        notifications.notify(charge.payerUserId(), NotificationType.LEDGER_UPDATE, "Amount due to WaDii",
                "A " + label(charge.type()) + " of EGP " + e.getAmount() + " is due. " + charge.ruleDescription(),
                "LEDGER", e.getId());
        return e;
    }

    @Transactional(readOnly = true)
    public PageResponse<View> mine(Long payerUserId, Long providerId, LedgerEntry.Type type, LedgerEntry.Status status,
                                   Integer page, Integer size) {
        return PageResponse.of(repository.search(payerUserId, providerId, type, status, PageResponse.pageable(page, size)), View::of);
    }

    @Transactional
    public View reportPaid(AuthUser me, Long id, ReportPaidRequest request) {
        LedgerEntry e = entry(id);
        if (!e.getPayerUserId().equals(me.id())) {
            throw BusinessException.forbidden();
        }
        if (e.getStatus() != LedgerEntry.Status.DUE) {
            throw BusinessException.of(ErrorCode.LEDGER_INVALID_STATE);
        }
        e.setStatus(LedgerEntry.Status.REPORTED_PAID);
        e.setReportedAt(Instant.now(clock));
        e.setMethod(request.method());
        e.setReference(request.reference());
        audit.record("LEDGER_REPORTED_PAID", "LEDGER", id, request.method().name());
        return View.of(e);
    }

    @Transactional
    public View settle(Long id, SettleRequest request) {
        LedgerEntry e = entry(id);
        if (!e.isOpen()) {
            throw BusinessException.of(ErrorCode.LEDGER_INVALID_STATE);
        }
        Instant now = Instant.now(clock);
        e.setStatus(LedgerEntry.Status.SETTLED);
        e.setSettledAt(now);
        e.setMethod(request.method());
        if (request.reference() != null) e.setReference(request.reference());
        if (request.note() != null) e.setNote(request.note());
        audit.record("LEDGER_SETTLED", "LEDGER", id, request.method() + " " + e.getAmount());
        notifications.notify(e.getPayerUserId(), NotificationType.LEDGER_UPDATE, "Payment confirmed",
                "WaDii confirmed your payment of EGP " + e.getAmount() + ".", "LEDGER", id);
        Instant paidAt = e.getReportedAt() != null ? e.getReportedAt() : now;
        events.publishEvent(new EntrySettled(id, e.getType(), e.getProviderId(),
                paidAt.atZone(com.doubleclick.wadii.common.util.Times.CAIRO).toLocalDate()));
        return View.of(e);
    }

    @Transactional
    public View waive(Long id, String note) {
        LedgerEntry e = entry(id);
        if (!e.isOpen()) {
            throw BusinessException.of(ErrorCode.LEDGER_INVALID_STATE);
        }
        e.setStatus(LedgerEntry.Status.WAIVED);
        e.setNote(note);
        audit.record("LEDGER_WAIVED", "LEDGER", id, note);
        return View.of(e);
    }

    /** Cancels open entries tied to an order whose basis disappeared (e.g. a dispute changed the outcome). */
    @Transactional
    public void cancelOpen(LedgerEntry e, String note) {
        if (e.isOpen()) {
            e.setStatus(LedgerEntry.Status.CANCELED);
            e.setNote(note);
            audit.record("LEDGER_CANCELED", "LEDGER", e.getId(), note);
        }
    }

    /** Manual adjustment by finance. Positive = the user owes WaDii more; it is recorded, never auto-collected. */
    @Transactional
    public View adjustment(AuthUser admin, Long payerUserId, Long providerId, BigDecimal amount, String note) {
        if (!Money.isPositive(amount)) {
            throw BusinessException.validation("amount must be positive; use waive to cancel an amount due.");
        }
        LedgerEntry e = due(new Charge(LedgerEntry.Type.ADJUSTMENT, amount, payerUserId, providerId, null, null, null,
                "Manual adjustment: " + note, null, null));
        e.setCreatedBy(admin.id());
        e.setNote(note);
        return View.of(e);
    }

    private LedgerEntry entry(Long id) {
        return repository.findById(id).orElseThrow(() -> BusinessException.of(ErrorCode.NOT_FOUND));
    }

    private static String label(LedgerEntry.Type type) {
        return switch (type) {
            case SUBSCRIPTION -> "provider subscription";
            case COMMISSION -> "service commission";
            case SELLER_FEE -> "seller fee";
            case CANCELLATION_FEE -> "cancellation fee";
            case ADJUSTMENT -> "balance adjustment";
        };
    }
}
