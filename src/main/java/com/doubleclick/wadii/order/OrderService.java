package com.doubleclick.wadii.order;

import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.common.util.KeyList;
import com.doubleclick.wadii.common.util.Money;
import com.doubleclick.wadii.common.util.Times;
import com.doubleclick.wadii.dispute.Dispute;
import com.doubleclick.wadii.dispute.DisputeRecorder;
import com.doubleclick.wadii.files.FileAccessRule;
import com.doubleclick.wadii.files.FilePurpose;
import com.doubleclick.wadii.files.FileService;
import com.doubleclick.wadii.fraud.FraudService;
import com.doubleclick.wadii.ledger.LedgerEntry;
import com.doubleclick.wadii.ledger.LedgerService;
import com.doubleclick.wadii.notification.NotificationService;
import com.doubleclick.wadii.notification.NotificationType;
import com.doubleclick.wadii.offer.OfferLineRepository;
import com.doubleclick.wadii.offer.OfferPricing;
import com.doubleclick.wadii.request.RequestStatusService;
import com.doubleclick.wadii.request.ServiceItem;
import com.doubleclick.wadii.request.ServiceItemRepository;
import com.doubleclick.wadii.settings.Configs;
import com.doubleclick.wadii.settings.PlatformConfigService;
import com.doubleclick.wadii.warranty.WarrantyService;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Service Order lifecycle after acceptance. Execution: ACCEPTED -> IN_PROGRESS -> PROVIDER_MARKED_COMPLETED ->
 * COMPLETED, only ever completed by the customer (or by staff deciding a dispute). Cash collection is a separate
 * record: the provider reports, the customer confirms or disputes, and nothing is inferred from execution.
 */
@Service
@RequiredArgsConstructor
public class OrderService implements FileAccessRule {

    public static final String FILE_REF = "ORDER";

    /** Order states in which the provider may record cash received. */
    static final Set<ServiceOrder.Status> CASH_REPORTABLE = EnumSet.of(ServiceOrder.Status.IN_PROGRESS,
            ServiceOrder.Status.PROVIDER_MARKED_COMPLETED, ServiceOrder.Status.COMPLETED, ServiceOrder.Status.INTERRUPTED);
    static final Set<ServiceOrder.CashStatus> CASH_OPEN = EnumSet.of(ServiceOrder.CashStatus.NOT_DUE,
            ServiceOrder.CashStatus.DUE, ServiceOrder.CashStatus.PARTIAL);

    private final ServiceOrderRepository orders;
    private final OrderChargeRepository charges;
    private final RatingRepository ratings;
    private final OfferLineRepository lines;
    private final ServiceItemRepository items;
    private final RequestStatusService requestStatus;
    private final AppointmentPolicy appointments;
    private final OrderTimeline timeline;
    private final WarrantyService warranties;
    private final DisputeRecorder disputes;
    private final LedgerService ledger;
    private final FraudService fraud;
    private final FileService files;
    private final NotificationService notifications;
    private final PlatformConfigService config;
    private final Clock clock;

    public enum Party { CUSTOMER, PROVIDER }

    public record NoteInput(@Size(max = 1000) String note) {
    }

    public record ReasonInput(@NotBlank @Size(max = 500) String reason) {
    }

    public record RescheduleInput(@NotNull Instant appointmentStart, @Size(max = 500) String reason) {
    }

    public record ComplaintInput(@NotBlank @Size(max = 4000) String description, @Size(max = 10) List<String> evidenceKeys) {
    }

    public record StatementInput(@NotBlank @Size(max = 4000) String workDone,
                                 @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal laborCost,
                                 @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal partsCost,
                                 @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal proposedAmount,
                                 @Size(max = 10) List<String> evidenceKeys) {
    }

    public record CashInput(@NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount) {
    }

    public record DescriptionInput(@NotBlank @Size(max = 4000) String description) {
    }

    public record ChargeInput(@NotBlank @Size(max = 500) String description,
                              @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount) {
    }

    public record RatingInput(@Min(1) @Max(5) int stars, @Size(max = 2000) String comment) {
    }

    // ------------------------------------------------------------------ execution

    @Transactional
    public ServiceOrder start(AuthUser user, Long id) {
        ServiceOrder o = locked(id);
        requireParty(o, user, Party.PROVIDER);
        requireStatus(o, ServiceOrder.Status.ACCEPTED);
        o.setStatus(ServiceOrder.Status.IN_PROGRESS);
        o.setStartedAt(now());
        timeline.record(o, "STARTED", user.id(), OrderEvent.ActorRole.PROVIDER, null);
        notifyCustomer(o, NotificationType.SERVICE_STARTED, "Service started", "The provider started working on your order.");
        return o;
    }

    @Transactional
    public ServiceOrder markCompleted(AuthUser user, Long id, String note) {
        ServiceOrder o = locked(id);
        requireParty(o, user, Party.PROVIDER);
        requireStatus(o, ServiceOrder.Status.IN_PROGRESS);
        o.setStatus(ServiceOrder.Status.PROVIDER_MARKED_COMPLETED);
        o.setProviderCompletedAt(now());
        if (o.getCashStatus() == ServiceOrder.CashStatus.NOT_DUE) {
            o.setCashStatus(ServiceOrder.CashStatus.DUE);
        }
        timeline.record(o, "PROVIDER_MARKED_COMPLETED", user.id(), OrderEvent.ActorRole.PROVIDER, note);
        notifyCustomer(o, NotificationType.CONFIRMATION_REQUIRED, "Please confirm completion",
                "The provider marked the service as completed. Review it and confirm, or tell us what is wrong.");
        return o;
    }

    @Transactional
    public ServiceOrder confirmCompletion(AuthUser user, Long id) {
        ServiceOrder o = locked(id);
        requireParty(o, user, Party.CUSTOMER);
        requireStatus(o, ServiceOrder.Status.PROVIDER_MARKED_COMPLETED);
        complete(o, user.id(), OrderEvent.ActorRole.CUSTOMER, null);
        return o;
    }

    @Transactional
    public Dispute complain(AuthUser user, Long id, ComplaintInput input) {
        ServiceOrder o = locked(id);
        requireParty(o, user, Party.CUSTOMER);
        requireStatus(o, ServiceOrder.Status.PROVIDER_MARKED_COMPLETED);
        o.setStatus(ServiceOrder.Status.DISPUTED);
        timeline.record(o, "COMPLAINT", user.id(), OrderEvent.ActorRole.CUSTOMER, input.description());
        return disputes.open(user.id(), subject(o, Dispute.Type.SERVICE_QUALITY, Party.CUSTOMER), input.description(), input.evidenceKeys());
    }

    // ------------------------------------------------------------------ before work starts

    @Transactional
    public ServiceOrder cancel(AuthUser user, Long id, String reason) {
        ServiceOrder o = locked(id);
        Party party = party(o, user);
        requireStatus(o, ServiceOrder.Status.ACCEPTED);
        cancelInternal(o, party == Party.CUSTOMER ? ServiceOrder.CancellationKind.CUSTOMER_CANCELED
                : ServiceOrder.CancellationKind.PROVIDER_CANCELED, reason, user.id(), role(party));
        return o;
    }

    @Transactional
    public ServiceOrder reschedule(AuthUser user, Long id, RescheduleInput input) {
        ServiceOrder o = locked(id);
        Party party = party(o, user);
        requireStatus(o, ServiceOrder.Status.ACCEPTED);
        Integer minutes = lines.findById(o.getLineId()).map(l -> l.getEstimatedMinutes()).orElse(null);
        AppointmentPolicy.Slot slot = appointments.reserve(o.getBranchId(), input.appointmentStart(), minutes, o.getId());
        Instant previous = o.getAppointmentStart();
        o.setAppointmentStart(slot.start());
        o.setAppointmentEnd(slot.end());
        timeline.record(o, "RESCHEDULED", user.id(), role(party),
                (previous == null ? "Set to " : "From " + previous + " to ") + slot.start()
                        + (input.reason() == null ? "" : " - " + input.reason()));
        notifyOther(o, party, NotificationType.APPOINTMENT_RESCHEDULED, "Appointment changed",
                "The appointment for order #" + o.getId() + " is now " + slot.start() + ".");
        return o;
    }

    /** After the grace period either party may report that the other did not show up; the order is canceled. */
    @Transactional
    public ServiceOrder reportNoShow(AuthUser user, Long id) {
        ServiceOrder o = locked(id);
        Party party = party(o, user);
        requireStatus(o, ServiceOrder.Status.ACCEPTED);
        if (!noShowReportable(o)) {
            throw BusinessException.of(ErrorCode.NO_SHOW_TOO_EARLY);
        }
        ServiceOrder.CancellationKind kind = party == Party.CUSTOMER ? ServiceOrder.CancellationKind.PROVIDER_NO_SHOW
                : ServiceOrder.CancellationKind.CUSTOMER_NO_SHOW;
        cancelInternal(o, kind, "No-show reported", user.id(), role(party));
        return o;
    }

    // ------------------------------------------------------------------ interruption / partial completion

    @Transactional
    public ServiceOrder requestInterruption(AuthUser user, Long id, String reason) {
        ServiceOrder o = locked(id);
        requireParty(o, user, Party.CUSTOMER);
        requireStatus(o, ServiceOrder.Status.IN_PROGRESS);
        o.setStatus(ServiceOrder.Status.INTERRUPTION_REQUESTED);
        o.setInterruptionRequestedAt(now());
        o.setInterruptionReason(reason);
        timeline.record(o, "INTERRUPTION_REQUESTED", user.id(), OrderEvent.ActorRole.CUSTOMER, reason);
        notifyProvider(o, NotificationType.INTERRUPTION_REQUESTED, "Customer asked to stop the work",
                "Record the work already done and the amount you propose for it.");
        return o;
    }

    @Transactional
    public ServiceOrder interruptionStatement(AuthUser user, Long id, StatementInput input) {
        ServiceOrder o = locked(id);
        requireParty(o, user, Party.PROVIDER);
        requireStatus(o, ServiceOrder.Status.INTERRUPTION_REQUESTED);
        if (input.proposedAmount().compareTo(o.amountDue()) > 0) {
            throw BusinessException.validation("The proposed amount cannot exceed the agreed amount of EGP " + o.amountDue() + ".");
        }
        files.attach(user.id(), input.evidenceKeys(), FilePurpose.EVIDENCE, FILE_REF, o.getId());
        o.setInterruptionWorkDone(input.workDone().trim());
        o.setInterruptionLabor(Money.of(input.laborCost()));
        o.setInterruptionParts(Money.of(input.partsCost()));
        o.setInterruptionProposed(Money.of(input.proposedAmount()));
        o.setInterruptionEvidence(KeyList.join(input.evidenceKeys()));
        o.setInterruptionStatementAt(now());
        o.setStatus(ServiceOrder.Status.INTERRUPTION_STATEMENT_SUBMITTED);
        timeline.record(o, "INTERRUPTION_STATEMENT", user.id(), OrderEvent.ActorRole.PROVIDER, "Proposed EGP " + o.getInterruptionProposed());
        notifyCustomer(o, NotificationType.INTERRUPTION_STATEMENT, "Partial-completion statement",
                "The provider proposes EGP " + o.getInterruptionProposed() + " for the work done. Accept it or dispute it.");
        return o;
    }

    @Transactional
    public ServiceOrder acceptInterruption(AuthUser user, Long id) {
        ServiceOrder o = locked(id);
        requireParty(o, user, Party.CUSTOMER);
        requireStatus(o, ServiceOrder.Status.INTERRUPTION_STATEMENT_SUBMITTED);
        o.setInterruptionOutcome(ServiceOrder.InterruptionOutcome.ACCEPTED);
        finishInterrupted(o, o.getInterruptionProposed(), user.id(), OrderEvent.ActorRole.CUSTOMER);
        notifyProvider(o, NotificationType.INTERRUPTION_ACCEPTED, "Statement accepted",
                "The customer accepted EGP " + o.getFinalAmount() + " for the work done.");
        return o;
    }

    @Transactional
    public Dispute disputeInterruption(AuthUser user, Long id, ComplaintInput input) {
        ServiceOrder o = locked(id);
        requireParty(o, user, Party.CUSTOMER);
        requireStatus(o, ServiceOrder.Status.INTERRUPTION_STATEMENT_SUBMITTED);
        o.setInterruptionOutcome(ServiceOrder.InterruptionOutcome.DISPUTED);
        o.setStatus(ServiceOrder.Status.DISPUTED);
        timeline.record(o, "INTERRUPTION_DISPUTED", user.id(), OrderEvent.ActorRole.CUSTOMER, input.description());
        return disputes.open(user.id(), subject(o, Dispute.Type.INTERRUPTION, Party.CUSTOMER), input.description(), input.evidenceKeys());
    }

    // ------------------------------------------------------------------ cash (self-reported, not verified by WaDii)

    @Transactional
    public ServiceOrder reportCash(AuthUser user, Long id, BigDecimal amount) {
        ServiceOrder o = locked(id);
        requireParty(o, user, Party.PROVIDER);
        if (!CASH_REPORTABLE.contains(o.getStatus())) {
            throw BusinessException.of(ErrorCode.ORDER_INVALID_STATE);
        }
        if (!CASH_OPEN.contains(o.getCashStatus())) {
            throw BusinessException.of(ErrorCode.CASH_INVALID_STATE);
        }
        BigDecimal reported = Money.of(amount);
        o.setCashReportedAmount(reported);
        o.setCashReportedAt(now());
        o.setCashConfirmedAt(null);
        o.setCashStatus(reported.compareTo(o.amountDue()) >= 0 ? ServiceOrder.CashStatus.REPORTED_COLLECTED : ServiceOrder.CashStatus.PARTIAL);
        timeline.record(o, "CASH_REPORTED", user.id(), OrderEvent.ActorRole.PROVIDER, "EGP " + reported);
        notifyCustomer(o, NotificationType.CASH_REPORTED, "Cash payment reported",
                "The provider reports receiving EGP " + reported + " in cash. Confirm it or tell us if this is wrong.");
        return o;
    }

    @Transactional
    public ServiceOrder confirmCash(AuthUser user, Long id) {
        ServiceOrder o = locked(id);
        requireParty(o, user, Party.CUSTOMER);
        if (!cashAwaitingCustomer(o)) {
            throw BusinessException.of(ErrorCode.CASH_INVALID_STATE);
        }
        o.setCashConfirmedAt(now());
        if (o.getCashStatus() == ServiceOrder.CashStatus.REPORTED_COLLECTED) {
            o.setCashStatus(ServiceOrder.CashStatus.CONFIRMED);
        }
        timeline.record(o, "CASH_CONFIRMED", user.id(), OrderEvent.ActorRole.CUSTOMER, "EGP " + o.getCashReportedAmount());
        notifyProvider(o, NotificationType.CASH_CONFIRMED, "Cash confirmed", "The customer confirmed the cash payment.");
        return o;
    }

    @Transactional
    public Dispute disputeCash(AuthUser user, Long id, String description) {
        ServiceOrder o = locked(id);
        requireParty(o, user, Party.CUSTOMER);
        if (!cashAwaitingCustomer(o)) {
            throw BusinessException.of(ErrorCode.CASH_INVALID_STATE);
        }
        o.setCashStatus(ServiceOrder.CashStatus.DISPUTED);
        timeline.record(o, "CASH_DISPUTED", user.id(), OrderEvent.ActorRole.CUSTOMER, description);
        fraud.cashConflict(o);
        notifyProvider(o, NotificationType.CASH_DISPUTED, "Cash report disputed", "The customer disputes the cash you reported.");
        return disputes.open(user.id(), subject(o, Dispute.Type.CASH_COLLECTION, Party.CUSTOMER), description, List.of());
    }

    // ------------------------------------------------------------------ additional charges and rating

    @Transactional
    public OrderCharge proposeCharge(AuthUser user, Long id, ChargeInput input) {
        ServiceOrder o = locked(id);
        requireParty(o, user, Party.PROVIDER);
        requireStatus(o, ServiceOrder.Status.IN_PROGRESS);
        OrderCharge c = new OrderCharge();
        c.setOrderId(o.getId());
        c.setDescription(input.description().trim());
        c.setAmount(Money.of(input.amount()));
        charges.save(c);
        timeline.record(o, "CHARGE_PROPOSED", user.id(), OrderEvent.ActorRole.PROVIDER, c.getDescription() + " EGP " + c.getAmount());
        notifyCustomer(o, NotificationType.CHARGE_PROPOSED, "Additional work proposed",
                "The provider asks for EGP " + c.getAmount() + " for: " + c.getDescription() + ". It applies only if you approve.");
        return c;
    }

    @Transactional
    public OrderCharge decideCharge(AuthUser user, Long id, Long chargeId, boolean approve) {
        ServiceOrder o = locked(id);
        requireParty(o, user, Party.CUSTOMER);
        OrderCharge c = charges.findByIdAndOrderId(chargeId, id).orElseThrow(() -> BusinessException.of(ErrorCode.NOT_FOUND));
        if (c.getStatus() != OrderCharge.Status.PENDING || o.isFinished()) {
            throw BusinessException.of(ErrorCode.ORDER_INVALID_STATE);
        }
        c.setStatus(approve ? OrderCharge.Status.APPROVED : OrderCharge.Status.REJECTED);
        c.setDecidedAt(now());
        if (approve) {
            o.setApprovedChargesTotal(o.getApprovedChargesTotal().add(c.getAmount()));
        }
        timeline.record(o, approve ? "CHARGE_APPROVED" : "CHARGE_REJECTED", user.id(), OrderEvent.ActorRole.CUSTOMER, c.getDescription());
        notifyProvider(o, NotificationType.CHARGE_DECIDED, approve ? "Additional work approved" : "Additional work rejected",
                c.getDescription() + " (EGP " + c.getAmount() + ")");
        return c;
    }

    @Transactional
    public Rating rate(AuthUser user, Long id, RatingInput input) {
        ServiceOrder o = locked(id);
        requireParty(o, user, Party.CUSTOMER);
        if (o.getStatus() != ServiceOrder.Status.COMPLETED) {
            throw BusinessException.of(ErrorCode.RATING_NOT_ALLOWED);
        }
        if (ratings.existsByOrderId(id)) {
            throw BusinessException.of(ErrorCode.RATING_ALREADY_EXISTS);
        }
        Rating r = new Rating();
        r.setOrderId(id);
        r.setCustomerId(user.id());
        r.setProviderId(o.getProviderId());
        r.setStars(input.stars());
        r.setComment(input.comment() == null || input.comment().isBlank() ? null : input.comment().trim());
        ratings.save(r);
        notifyProvider(o, NotificationType.RATING_RECEIVED, "New rating", "A customer rated your service " + input.stars() + "/5.");
        return r;
    }

    // ------------------------------------------------------------------ staff decisions

    @Transactional
    public ServiceOrder adminCancel(AuthUser admin, Long id, String reason) {
        ServiceOrder o = locked(id);
        if (o.isFinished()) {
            throw BusinessException.of(ErrorCode.ORDER_INVALID_STATE);
        }
        cancelInternal(o, ServiceOrder.CancellationKind.ADMIN_CANCELED, reason, admin.id(), OrderEvent.ActorRole.ADMIN);
        return o;
    }

    /** Applies a dispute decision to the order. Called only by the dispute module after its permission checks. */
    @Transactional
    public void applyOutcome(Long orderId, Dispute.Outcome outcome, BigDecimal finalAmount, Long adminId, String note) {
        ServiceOrder o = locked(orderId);
        switch (outcome) {
            case COMPLETE_ORDER -> {
                requireStatus(o, ServiceOrder.Status.DISPUTED);
                complete(o, adminId, OrderEvent.ActorRole.ADMIN, finalAmount);
            }
            case REOPEN_ORDER -> {
                requireStatus(o, ServiceOrder.Status.DISPUTED);
                o.setStatus(ServiceOrder.Status.IN_PROGRESS);
                timeline.record(o, "REOPENED", adminId, OrderEvent.ActorRole.ADMIN, note);
                notifyBoth(o, NotificationType.DISPUTE_RESOLVED, "Work reopened", "Support reopened the order so the work can be finished.");
            }
            case CANCEL_ORDER -> {
                if (o.isFinished()) throw BusinessException.of(ErrorCode.ORDER_INVALID_STATE);
                cancelInternal(o, ServiceOrder.CancellationKind.ADMIN_CANCELED, note, adminId, OrderEvent.ActorRole.ADMIN);
            }
            case SET_FINAL_AMOUNT -> {
                if (finalAmount == null || finalAmount.signum() < 0) {
                    throw BusinessException.validation("finalAmount is required for this outcome.");
                }
                requireStatus(o, ServiceOrder.Status.DISPUTED);
                if (o.getInterruptionRequestedAt() != null) {
                    o.setInterruptionOutcome(ServiceOrder.InterruptionOutcome.RESOLVED);
                    finishInterrupted(o, finalAmount, adminId, OrderEvent.ActorRole.ADMIN);
                } else {
                    complete(o, adminId, OrderEvent.ActorRole.ADMIN, finalAmount);
                }
            }
            case CASH_CONFIRMED -> {
                requireCash(o, ServiceOrder.CashStatus.DISPUTED);
                o.setCashStatus(ServiceOrder.CashStatus.CONFIRMED);
                o.setCashConfirmedAt(now());
                timeline.record(o, "CASH_CONFIRMED_BY_SUPPORT", adminId, OrderEvent.ActorRole.ADMIN, note);
            }
            case CASH_NOT_COLLECTED -> {
                requireCash(o, ServiceOrder.CashStatus.DISPUTED);
                o.setCashStatus(ServiceOrder.CashStatus.DUE);
                o.setCashReportedAmount(null);
                o.setCashReportedAt(null);
                timeline.record(o, "CASH_REPORT_REJECTED_BY_SUPPORT", adminId, OrderEvent.ActorRole.ADMIN, note);
            }
            default -> timeline.record(o, "DISPUTE_CLOSED", adminId, OrderEvent.ActorRole.ADMIN, outcome + (note == null ? "" : ": " + note));
        }
    }

    /** Puts an order back to its pre-dispute state when staff reject a dispute without changing anything. */
    @Transactional
    public void restoreAfterRejectedDispute(Long orderId, Dispute.Type type, Long adminId) {
        ServiceOrder o = locked(orderId);
        if (type == Dispute.Type.CASH_COLLECTION && o.getCashStatus() == ServiceOrder.CashStatus.DISPUTED) {
            o.setCashStatus(o.getCashReportedAmount() != null && o.getCashReportedAmount().compareTo(o.amountDue()) >= 0
                    ? ServiceOrder.CashStatus.CONFIRMED : ServiceOrder.CashStatus.PARTIAL);
            o.setCashConfirmedAt(now());
        } else if (type == Dispute.Type.SERVICE_QUALITY && o.getStatus() == ServiceOrder.Status.DISPUTED) {
            complete(o, adminId, OrderEvent.ActorRole.ADMIN, null);
        } else if (type == Dispute.Type.INTERRUPTION && o.getStatus() == ServiceOrder.Status.DISPUTED) {
            o.setInterruptionOutcome(ServiceOrder.InterruptionOutcome.RESOLVED);
            finishInterrupted(o, o.getInterruptionProposed(), adminId, OrderEvent.ActorRole.ADMIN);
        }
    }

    // ------------------------------------------------------------------ queries used by views

    boolean noShowReportable(ServiceOrder o) {
        if (o.getAppointmentStart() == null) return false;
        int grace = config.marketplace().noShowGraceMinutes();
        return !now().isBefore(o.getAppointmentStart().plus(Duration.ofMinutes(grace)));
    }

    static boolean cashAwaitingCustomer(ServiceOrder o) {
        return o.getCashStatus() == ServiceOrder.CashStatus.REPORTED_COLLECTED
                || (o.getCashStatus() == ServiceOrder.CashStatus.PARTIAL && o.getCashConfirmedAt() == null && o.getCashReportedAt() != null);
    }

    @Transactional(readOnly = true)
    public ServiceOrder visible(AuthUser user, Long id) {
        ServiceOrder o = orders.findById(id).orElseThrow(() -> BusinessException.of(ErrorCode.ORDER_NOT_FOUND));
        if (!user.admin() && !o.getCustomerId().equals(user.id()) && !o.getProviderUserId().equals(user.id())) {
            throw BusinessException.of(ErrorCode.ORDER_NOT_FOUND);
        }
        return o;
    }

    @Override
    public boolean supports(String refType) {
        return FILE_REF.equals(refType);
    }

    @Override
    public boolean canRead(AuthUser user, String refType, Long refId) {
        return orders.findById(refId).map(o -> o.getCustomerId().equals(user.id()) || o.getProviderUserId().equals(user.id()))
                .orElse(false);
    }

    // ------------------------------------------------------------------ internals

    private void complete(ServiceOrder o, Long actorId, OrderEvent.ActorRole role, BigDecimal finalAmount) {
        if (finalAmount != null) {
            o.setFinalAmount(Money.of(finalAmount));
        }
        o.setStatus(ServiceOrder.Status.COMPLETED);
        o.setCompletedAt(now());
        o.setInvoiceNumber("WD-" + Times.local(o.getCompletedAt()).getYear() + "-" + String.format("%06d", o.getId()));
        if (o.getCashStatus() == ServiceOrder.CashStatus.NOT_DUE) {
            o.setCashStatus(ServiceOrder.CashStatus.DUE);
        }
        warranties.activate(o);
        accrueCommission(o);
        timeline.record(o, "COMPLETED", actorId, role, "Invoice " + o.getInvoiceNumber());
        notifyBoth(o, NotificationType.ORDER_COMPLETED, "Order completed",
                "Order #" + o.getId() + " is completed. Invoice " + o.getInvoiceNumber() + ".");
    }

    private void finishInterrupted(ServiceOrder o, BigDecimal amount, Long actorId, OrderEvent.ActorRole role) {
        o.setFinalAmount(Money.of(amount));
        o.setStatus(ServiceOrder.Status.INTERRUPTED);
        o.setCompletedAt(now());
        if (o.getCashStatus() == ServiceOrder.CashStatus.NOT_DUE && o.getFinalAmount().signum() > 0) {
            o.setCashStatus(ServiceOrder.CashStatus.DUE);
        }
        accrueCommission(o);
        timeline.record(o, "INTERRUPTED", actorId, role, "Final amount EGP " + o.getFinalAmount());
    }

    /**
     * Records the commission WaDii is owed. The provider collects all cash, so the provider remits it whichever
     * party bears it. With a final amount (partial completion or a dispute), the commission is recomputed on it.
     */
    private void accrueCommission(ServiceOrder o) {
        BigDecimal base = o.getFinalAmount() != null ? o.getFinalAmount() : o.getCommissionBase();
        BigDecimal amount = o.getFinalAmount() != null
                ? OfferPricing.commission(o.getCommissionMode(), o.getCommissionValue(), base)
                : o.getCommissionAmount();
        String rule = switch (o.getCommissionMode()) {
            case NONE -> "No commission";
            case FIXED -> "Fixed commission of EGP " + o.getCommissionValue() + " per service order";
            case PERCENT -> o.getCommissionValue() + "% of EGP " + base;
        } + (o.getCommissionPayer() == Configs.CommissionPayer.CUSTOMER
                ? " (customer-paid surcharge collected in cash by the provider)" : " (provider-paid)");
        ledger.due(new LedgerService.Charge(LedgerEntry.Type.COMMISSION, amount, o.getProviderUserId(), o.getProviderId(),
                o.getId(), o.getRequestId(), null, rule, o.getCommissionConfigId(), Times.today(clock).plusDays(30)));
    }

    private void cancelInternal(ServiceOrder o, ServiceOrder.CancellationKind kind, String reason, Long actorId,
                                OrderEvent.ActorRole role) {
        o.setStatus(ServiceOrder.Status.CANCELED);
        o.setCanceledAt(now());
        o.setCancellationKind(kind);
        o.setCancellationReason(reason);
        o.setCanceledBy(actorId);
        ServiceItem item = items.findById(o.getServiceItemId()).orElseThrow();
        item.setStatus(ServiceItem.Status.CANCELED);
        requestStatus.refresh(o.getRequestId());
        timeline.record(o, kind.name(), actorId, role, reason);
        String body = switch (kind) {
            case CUSTOMER_CANCELED -> "The customer canceled order #" + o.getId() + ".";
            case PROVIDER_CANCELED -> "The provider canceled order #" + o.getId() + ".";
            case CUSTOMER_NO_SHOW -> "Order #" + o.getId() + " was canceled: the provider reported that the customer did not show up.";
            case PROVIDER_NO_SHOW -> "Order #" + o.getId() + " was canceled: the customer reported that the provider did not show up.";
            case ADMIN_CANCELED -> "WaDii support canceled order #" + o.getId() + ".";
        };
        NotificationType type = kind == ServiceOrder.CancellationKind.CUSTOMER_NO_SHOW || kind == ServiceOrder.CancellationKind.PROVIDER_NO_SHOW
                ? NotificationType.NO_SHOW_REPORTED : NotificationType.ORDER_CANCELED;
        notifyBoth(o, type, "Order canceled", body);
        if (ProviderStats.PROVIDER_FAULT.contains(kind)) {
            fraud.afterProviderFault(o.getProviderId(), o.getProviderUserId());
        } else if (kind != ServiceOrder.CancellationKind.ADMIN_CANCELED) {
            fraud.afterCustomerCancellation(o.getCustomerId());
        }
    }

    private DisputeRecorder.Subject subject(ServiceOrder o, Dispute.Type type, Party opener) {
        Long respondent = opener == Party.CUSTOMER ? o.getProviderUserId() : o.getCustomerId();
        return new DisputeRecorder.Subject(type, o.getId(), o.getRequestId(), null, null,
                opener == Party.CUSTOMER ? Dispute.PartyRole.CUSTOMER : Dispute.PartyRole.PROVIDER, respondent);
    }

    private ServiceOrder locked(Long id) {
        return orders.lockById(id).orElseThrow(() -> BusinessException.of(ErrorCode.ORDER_NOT_FOUND));
    }

    private Party party(ServiceOrder o, AuthUser user) {
        if (o.getCustomerId().equals(user.id())) return Party.CUSTOMER;
        if (o.getProviderUserId().equals(user.id())) return Party.PROVIDER;
        throw BusinessException.of(ErrorCode.ORDER_NOT_FOUND);
    }

    private void requireParty(ServiceOrder o, AuthUser user, Party expected) {
        if (party(o, user) != expected) {
            throw BusinessException.forbidden();
        }
    }

    private static void requireStatus(ServiceOrder o, ServiceOrder.Status expected) {
        if (o.getStatus() != expected) {
            throw BusinessException.of(ErrorCode.ORDER_INVALID_STATE);
        }
    }

    private static void requireCash(ServiceOrder o, ServiceOrder.CashStatus expected) {
        if (o.getCashStatus() != expected) {
            throw BusinessException.of(ErrorCode.CASH_INVALID_STATE);
        }
    }

    private static OrderEvent.ActorRole role(Party party) {
        return party == Party.CUSTOMER ? OrderEvent.ActorRole.CUSTOMER : OrderEvent.ActorRole.PROVIDER;
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private void notifyCustomer(ServiceOrder o, NotificationType type, String title, String body) {
        notifications.notify(o.getCustomerId(), type, title, body, "ORDER", o.getId());
    }

    private void notifyProvider(ServiceOrder o, NotificationType type, String title, String body) {
        notifications.notify(o.getProviderUserId(), type, title, body, "ORDER", o.getId());
    }

    private void notifyOther(ServiceOrder o, Party actor, NotificationType type, String title, String body) {
        if (actor == Party.CUSTOMER) notifyProvider(o, type, title, body);
        else notifyCustomer(o, type, title, body);
    }

    private void notifyBoth(ServiceOrder o, NotificationType type, String title, String body) {
        notifyCustomer(o, type, title, body);
        notifyProvider(o, type, title, body);
    }
}
