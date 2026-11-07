package com.doubleclick.wadii.dispute;

import com.doubleclick.wadii.audit.AuditService;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.identity.AdminRole;
import com.doubleclick.wadii.listing.Listing;
import com.doubleclick.wadii.listing.ListingRepository;
import com.doubleclick.wadii.notification.NotificationService;
import com.doubleclick.wadii.notification.NotificationType;
import com.doubleclick.wadii.order.OrderService;
import com.doubleclick.wadii.order.ServiceOrder;
import com.doubleclick.wadii.order.ServiceOrderRepository;
import com.doubleclick.wadii.warranty.WarrantyService;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Disputes as seen by the parties and decided by staff. Operational outcomes need the support role; any outcome
 * that changes money (a final amount, or a cash decision) also needs the finance role.
 */
@Service
@RequiredArgsConstructor
public class DisputeService {

    private static final Set<Dispute.Outcome> FINANCIAL = EnumSet.of(Dispute.Outcome.SET_FINAL_AMOUNT,
            Dispute.Outcome.CASH_CONFIRMED, Dispute.Outcome.CASH_NOT_COLLECTED);

    private final DisputeRepository disputes;
    private final DisputeRecorder recorder;
    private final ServiceOrderRepository orders;
    private final ListingRepository listings;
    private final OrderService orderService;
    private final WarrantyService warranties;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    public record OpenInput(@NotNull Dispute.Type type, Long orderId, Long listingId,
                            @NotBlank @Size(max = 4000) String description, @Size(max = 10) List<String> evidenceKeys) {
    }

    public record EvidenceInput(@Size(max = 4000) String note, String fileKey) {
    }

    public record ResolveInput(@NotNull Dispute.Outcome outcome, @NotBlank @Size(max = 4000) String note,
                               @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal finalAmount) {
    }

    public record NoteInput(@NotBlank @Size(max = 4000) String note) {
    }

    @Transactional(readOnly = true)
    public PageResponse<DisputeRecorder.View> mine(AuthUser user, Integer page, Integer size) {
        return PageResponse.of(disputes.involving(user.id(), PageResponse.pageable(page, size)), recorder::view);
    }

    @Transactional(readOnly = true)
    public DisputeRecorder.View detail(AuthUser user, Long id) {
        Dispute d = disputes.findById(id).orElseThrow(() -> BusinessException.of(ErrorCode.DISPUTE_NOT_FOUND));
        if (!user.admin() && !d.involves(user.id())) {
            throw BusinessException.of(ErrorCode.DISPUTE_NOT_FOUND);
        }
        return recorder.view(d);
    }

    /** Disputes not opened by an order or warranty action: cancellation/no-show disagreements, listings, other. */
    @Transactional
    public DisputeRecorder.View open(AuthUser user, OpenInput input) {
        if (!EnumSet.of(Dispute.Type.CANCELLATION, Dispute.Type.LISTING, Dispute.Type.OTHER).contains(input.type())) {
            throw BusinessException.validation("This dispute type is opened from the order or warranty screens.");
        }
        DisputeRecorder.Subject subject;
        if (input.orderId() != null) {
            ServiceOrder o = orders.findById(input.orderId()).orElseThrow(() -> BusinessException.of(ErrorCode.ORDER_NOT_FOUND));
            boolean customer = o.getCustomerId().equals(user.id());
            if (!customer && !o.getProviderUserId().equals(user.id())) {
                throw BusinessException.of(ErrorCode.ORDER_NOT_FOUND);
            }
            subject = new DisputeRecorder.Subject(input.type(), o.getId(), o.getRequestId(), null, null,
                    customer ? Dispute.PartyRole.CUSTOMER : Dispute.PartyRole.PROVIDER, customer ? o.getProviderUserId() : o.getCustomerId());
        } else if (input.listingId() != null) {
            Listing l = listings.findById(input.listingId()).orElseThrow(() -> BusinessException.of(ErrorCode.LISTING_NOT_FOUND));
            if (l.getSellerId().equals(user.id())) {
                throw BusinessException.validation("Sellers cannot open a dispute on their own listing.");
            }
            subject = new DisputeRecorder.Subject(Dispute.Type.LISTING, null, null, l.getId(), null, Dispute.PartyRole.BUYER, l.getSellerId());
        } else {
            throw BusinessException.validation("orderId or listingId is required.");
        }
        return recorder.view(recorder.open(user.id(), subject, input.description(), input.evidenceKeys()));
    }

    @Transactional(readOnly = true)
    public PageResponse<DisputeRecorder.View> adminList(Dispute.Status status, Dispute.Type type, Integer page, Integer size) {
        return PageResponse.of(disputes.search(status, type, PageResponse.pageable(page, size)), recorder::view);
    }

    @Transactional
    public DisputeRecorder.View assign(AuthUser admin, Long id) {
        Dispute d = lockedOpen(id);
        d.setAssignedAdminId(admin.id());
        d.setStatus(Dispute.Status.UNDER_REVIEW);
        audit.record("DISPUTE_ASSIGNED", "DISPUTE", id, null);
        notifyParties(d, NotificationType.DISPUTE_UPDATED, "Dispute under review", "A support agent is reviewing your dispute.");
        return recorder.view(d);
    }

    @Transactional
    public DisputeRecorder.View resolve(AuthUser admin, Long id, ResolveInput input) {
        AdminRole needed = FINANCIAL.contains(input.outcome()) ? AdminRole.FINANCE_ADMIN : AdminRole.SUPPORT_ADMIN;
        if (!admin.hasAdminRole(needed)) {
            throw BusinessException.forbidden("This outcome needs the " + needed + " role.");
        }
        Dispute d = lockedOpen(id);
        validateOutcome(d, input);
        if (d.getOrderId() != null && d.getType() != Dispute.Type.WARRANTY) {
            orderService.applyOutcome(d.getOrderId(), input.outcome(), input.finalAmount(), admin.id(), input.note());
        }
        if (d.getWarrantyClaimId() != null) {
            warranties.closeEscalated(d.getWarrantyClaimId(), input.outcome() == Dispute.Outcome.WARRANTY_HONORED);
        }
        close(d, admin, Dispute.Status.RESOLVED, input.outcome(), input.note(), input.finalAmount());
        return recorder.view(d);
    }

    /** Rejecting means the complaint does not stand; an order held by the dispute goes back to its normal course. */
    @Transactional
    public DisputeRecorder.View reject(AuthUser admin, Long id, String note) {
        Dispute d = lockedOpen(id);
        if (d.getOrderId() != null) {
            orderService.restoreAfterRejectedDispute(d.getOrderId(), d.getType(), admin.id());
        }
        if (d.getWarrantyClaimId() != null) {
            warranties.closeEscalated(d.getWarrantyClaimId(), false);
        }
        close(d, admin, Dispute.Status.REJECTED, Dispute.Outcome.NO_ACTION, note, null);
        return recorder.view(d);
    }

    private void validateOutcome(Dispute d, ResolveInput input) {
        Set<Dispute.Outcome> allowed = switch (d.getType()) {
            case SERVICE_QUALITY -> EnumSet.of(Dispute.Outcome.COMPLETE_ORDER, Dispute.Outcome.REOPEN_ORDER,
                    Dispute.Outcome.CANCEL_ORDER, Dispute.Outcome.SET_FINAL_AMOUNT);
            case INTERRUPTION -> EnumSet.of(Dispute.Outcome.SET_FINAL_AMOUNT, Dispute.Outcome.CANCEL_ORDER);
            case CASH_COLLECTION -> EnumSet.of(Dispute.Outcome.CASH_CONFIRMED, Dispute.Outcome.CASH_NOT_COLLECTED);
            case WARRANTY -> EnumSet.of(Dispute.Outcome.WARRANTY_HONORED, Dispute.Outcome.NO_ACTION);
            case CANCELLATION, LISTING, OTHER -> EnumSet.of(Dispute.Outcome.NO_ACTION);
        };
        if (!allowed.contains(input.outcome())) {
            throw BusinessException.validation("Allowed outcomes for " + d.getType() + ": " + allowed);
        }
    }

    private void close(Dispute d, AuthUser admin, Dispute.Status status, Dispute.Outcome outcome, String note, BigDecimal amount) {
        d.setStatus(status);
        d.setOutcome(outcome);
        d.setResolutionNote(note);
        d.setFinalAmount(amount);
        d.setResolvedBy(admin.id());
        d.setResolvedAt(Instant.now(clock));
        if (d.getAssignedAdminId() == null) d.setAssignedAdminId(admin.id());
        audit.record("DISPUTE_" + status, "DISPUTE", d.getId(), outcome + ": " + note);
        notifyParties(d, NotificationType.DISPUTE_RESOLVED, "Dispute decided",
                (status == Dispute.Status.RESOLVED ? "Decision: " + outcome : "The dispute was rejected") + ". " + note);
    }

    private Dispute lockedOpen(Long id) {
        Dispute d = disputes.lockById(id).orElseThrow(() -> BusinessException.of(ErrorCode.DISPUTE_NOT_FOUND));
        if (!d.isOpen()) {
            throw BusinessException.of(ErrorCode.DISPUTE_INVALID_STATE);
        }
        return d;
    }

    private void notifyParties(Dispute d, NotificationType type, String title, String body) {
        notifications.notify(d.getOpenedBy(), type, title, body, "DISPUTE", d.getId());
        notifications.notify(d.getRespondentUserId(), type, title, body, "DISPUTE", d.getId());
    }
}
