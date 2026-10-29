package com.doubleclick.wadii.warranty;

import com.doubleclick.wadii.common.config.WadiiProperties;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.dispute.DisputeService;
import com.doubleclick.wadii.dispute.DisputeType;
import com.doubleclick.wadii.notification.NotificationService;
import com.doubleclick.wadii.notification.NotificationType;
import com.doubleclick.wadii.order.ActorRole;
import com.doubleclick.wadii.order.Order;
import com.doubleclick.wadii.order.OrderAccess;
import com.doubleclick.wadii.order.OrderStatus;
import com.doubleclick.wadii.order.OrderTimelineService;
import com.doubleclick.wadii.order.TimelineEvent;
import com.doubleclick.wadii.payment.Payment;
import com.doubleclick.wadii.payment.PaymentService;
import com.doubleclick.wadii.payment.Refund;
import com.doubleclick.wadii.payment.RefundService;
import com.doubleclick.wadii.payment.RefundSource;
import com.doubleclick.wadii.policy.PolicyService;
import com.doubleclick.wadii.policy.PolicyType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Service warranties (from the WARRANTY policy snapshot), warranty claims, and product returns.
 * Return and refund are separate: a return may or may not come with a refund, and refunds exist without returns.
 */
@Service
@RequiredArgsConstructor
public class WarrantyService {

    private final ServiceWarrantyRepository warrantyRepository;
    private final WarrantyClaimRepository claimRepository;
    private final ReturnRequestRepository returnRepository;
    private final OrderAccess orderAccess;
    private final PolicyService policyService;
    private final DisputeService disputeService;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final NotificationService notifications;
    private final OrderTimelineService timeline;
    private final WadiiProperties properties;
    private final Clock clock;

    public record WarrantyView(Long id, Long orderId, Long providerId, LocalDate startDate, LocalDate endDate, String terms,
                               WarrantyStatus status, boolean validToday) {
    }

    public record ClaimRequest(@NotBlank @Size(max = 4000) String description) {
    }

    public record ClaimDecision(@NotNull Boolean accept, @Size(max = 4000) String response) {
    }

    public record ClaimView(Long id, Long warrantyId, String description, ClaimStatus status, String providerResponse,
                            Long disputeId, Instant createdAt, Instant decidedAt) {
        static ClaimView of(WarrantyClaim c) {
            return new ClaimView(c.getId(), c.getWarrantyId(), c.getDescription(), c.getStatus(), c.getProviderResponse(),
                    c.getDisputeId(), c.getCreatedAt(), c.getDecidedAt());
        }
    }

    public record ReturnCreate(@NotBlank @Size(max = 4000) String description) {
    }

    public record ReturnDecision(@NotNull Boolean approve, @Size(max = 1000) String note) {
    }

    public record ReturnReceived(@DecimalMin("0.01") BigDecimal refundAmount) {
    }

    public record ReturnView(Long id, Long orderId, String description, ReturnStatus status, Long refundId, String decisionNote,
                             Instant createdAt, Instant decidedAt) {
        static ReturnView of(ReturnRequest r) {
            return new ReturnView(r.getId(), r.getOrderId(), r.getDescription(), r.getStatus(), r.getRefundId(), r.getDecisionNote(),
                    r.getCreatedAt(), r.getDecidedAt());
        }
    }

    // ------------------------------------------------------------------ warranty

    /** Issued when the order completes, if the WARRANTY snapshot grants a period. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void issueFor(Order order) {
        Integer days = policyService.snapshotTerms(order.getId(), PolicyType.WARRANTY).periodDays();
        if (days == null || days <= 0 || warrantyRepository.findByOrderId(order.getId()).isPresent()) return;
        LocalDate start = today();
        ServiceWarranty w = new ServiceWarranty();
        w.setOrderId(order.getId());
        w.setProviderId(order.getAcceptedProvider().getId());
        w.setCustomerId(order.getCustomer().getId());
        w.setStartDate(start);
        w.setEndDate(start.plusDays(days));
        w.setTerms(policyService.snapshotTerms(order.getId(), PolicyType.WARRANTY).terms());
        warrantyRepository.save(w);
        timeline.system(order.getId(), TimelineEvent.WARRANTY_ISSUED, days + " days, until " + w.getEndDate());
    }

    @Transactional(readOnly = true)
    public WarrantyView forOrder(AuthUser me, Long orderId) {
        orderAccess.requireParty(me, orderAccess.load(orderId));
        return view(warrantyRepository.findByOrderId(orderId).orElseThrow(() -> BusinessException.notFound(ErrorCode.WARRANTY_NOT_FOUND)));
    }

    @Transactional
    public ClaimView claim(AuthUser me, Long orderId, ClaimRequest r) {
        Order order = orderAccess.load(orderId);
        orderAccess.requireCustomer(me, order);
        ServiceWarranty w = warrantyRepository.findByOrderId(orderId).orElseThrow(() -> BusinessException.notFound(ErrorCode.WARRANTY_NOT_FOUND));
        if (!w.isValidOn(today())) throw BusinessException.of(ErrorCode.WARRANTY_EXPIRED);
        WarrantyClaim c = new WarrantyClaim();
        c.setWarrantyId(w.getId());
        c.setDescription(r.description().trim());
        c.setCreatedBy(me.id());
        c = claimRepository.save(c);
        timeline.add(orderId, TimelineEvent.WARRANTY_CLAIMED, me.id(), ActorRole.CUSTOMER, r.description());
        notifications.notify(order.getAcceptedProvider().getUser().getId(), NotificationType.WARRANTY_CLAIM, "Warranty claim",
                "The customer filed a warranty claim on order #" + orderId + ".", orderId);
        return ClaimView.of(c);
    }

    @Transactional
    public ClaimView decideClaim(AuthUser me, Long claimId, ClaimDecision d) {
        WarrantyClaim c = loadClaim(claimId);
        Order order = orderOfClaim(c);
        orderAccess.requireAcceptedProvider(me, order);
        if (c.getStatus() != ClaimStatus.SUBMITTED) throw BusinessException.of(ErrorCode.WARRANTY_CLAIM_INVALID_STATE);
        c.setStatus(d.accept() ? ClaimStatus.ACCEPTED : ClaimStatus.REJECTED);
        c.setProviderResponse(d.response());
        c.setDecidedAt(clock.instant());
        notifications.notify(order.getCustomer().getId(), NotificationType.WARRANTY_CLAIM, d.accept() ? "Warranty claim accepted" : "Warranty claim rejected",
                "Your warranty claim on order #" + order.getId() + " was " + (d.accept() ? "accepted." : "rejected. You can escalate it to a dispute."), order.getId());
        return ClaimView.of(c);
    }

    @Transactional
    public ClaimView resolveClaim(AuthUser me, Long claimId) {
        WarrantyClaim c = loadClaim(claimId);
        Order order = orderOfClaim(c);
        orderAccess.requireAcceptedProvider(me, order);
        if (c.getStatus() != ClaimStatus.ACCEPTED) throw BusinessException.of(ErrorCode.WARRANTY_CLAIM_INVALID_STATE);
        c.setStatus(ClaimStatus.RESOLVED);
        return ClaimView.of(c);
    }

    /** Customer escalates a rejected (or stalled) claim into a WARRANTY_DISPUTE. */
    @Transactional
    public ClaimView escalate(AuthUser me, Long claimId) {
        WarrantyClaim c = loadClaim(claimId);
        Order order = orderAccess.loadForUpdate(orderOfClaim(c).getId());
        orderAccess.requireCustomer(me, order);
        if (c.getStatus() == ClaimStatus.RESOLVED || c.getStatus() == ClaimStatus.ESCALATED) {
            throw BusinessException.of(ErrorCode.WARRANTY_CLAIM_INVALID_STATE);
        }
        var dispute = disputeService.create(me, order, new DisputeService.OpenRequest(DisputeType.WARRANTY_DISPUTE,
                "Warranty claim #" + c.getId(), c.getDescription(), null), ActorRole.CUSTOMER);
        c.setStatus(ClaimStatus.ESCALATED);
        c.setDisputeId(dispute.id());
        return ClaimView.of(c);
    }

    @Transactional(readOnly = true)
    public List<ClaimView> claims(AuthUser me, Long orderId) {
        orderAccess.requireParty(me, orderAccess.load(orderId));
        return warrantyRepository.findByOrderId(orderId)
                .map(w -> claimRepository.findByWarrantyIdOrderByCreatedAtDesc(w.getId()).stream().map(ClaimView::of).toList())
                .orElse(List.of());
    }

    @Scheduled(cron = "0 15 0 * * *")
    @Transactional
    public void expireWarranties() {
        warrantyRepository.expireEndedBefore(today());
    }

    // ------------------------------------------------------------------ returns

    @Transactional
    public ReturnView requestReturn(AuthUser me, Long orderId, ReturnCreate r) {
        Order order = orderAccess.load(orderId);
        orderAccess.requireCustomer(me, order);
        if (order.getStatus() != OrderStatus.COMPLETED) throw BusinessException.of(ErrorCode.ORDER_INVALID_STATE, "Returns are possible after completion");
        Integer days = policyService.snapshotTerms(orderId, PolicyType.RETURN).periodDays();
        if (days == null || clock.instant().isAfter(order.getCompletedAt().plus(Duration.ofDays(days)))) {
            throw BusinessException.of(ErrorCode.RETURN_WINDOW_CLOSED);
        }
        ReturnRequest rr = new ReturnRequest();
        rr.setOrderId(orderId);
        rr.setRequestedBy(me.id());
        rr.setDescription(r.description().trim());
        rr = returnRepository.save(rr);
        timeline.add(orderId, TimelineEvent.RETURN_REQUESTED, me.id(), ActorRole.CUSTOMER, r.description());
        notifications.notify(order.getAcceptedProvider().getUser().getId(), NotificationType.RETURN_REQUEST, "Return requested",
                "The customer asked to return items from order #" + orderId + ".", orderId);
        return ReturnView.of(rr);
    }

    @Transactional
    public ReturnView decideReturn(AuthUser me, Long returnId, ReturnDecision d) {
        ReturnRequest rr = loadReturn(returnId);
        Order order = orderAccess.load(rr.getOrderId());
        orderAccess.requireAcceptedProvider(me, order);
        if (rr.getStatus() != ReturnStatus.REQUESTED) throw BusinessException.of(ErrorCode.RETURN_INVALID_STATE);
        rr.setStatus(d.approve() ? ReturnStatus.APPROVED : ReturnStatus.REJECTED);
        rr.setDecidedBy(me.id());
        rr.setDecisionNote(d.note());
        rr.setDecidedAt(clock.instant());
        notifications.notify(order.getCustomer().getId(), NotificationType.RETURN_REQUEST, d.approve() ? "Return approved" : "Return rejected",
                "Your return on order #" + order.getId() + " was " + (d.approve() ? "approved." : "rejected."), order.getId());
        return ReturnView.of(rr);
    }

    /** The provider received the items; optionally refunds an amount (the refund is a separate record). */
    @Transactional
    public ReturnView markReceived(AuthUser me, Long returnId, ReturnReceived r) {
        ReturnRequest rr = loadReturn(returnId);
        Order order = orderAccess.loadForUpdate(rr.getOrderId());
        orderAccess.requireAcceptedProvider(me, order);
        if (rr.getStatus() != ReturnStatus.APPROVED) throw BusinessException.of(ErrorCode.RETURN_INVALID_STATE);
        rr.setStatus(ReturnStatus.RECEIVED);
        if (r != null && r.refundAmount() != null) {
            Payment p = paymentService.confirmedPayments(order.getId()).stream()
                    .filter(x -> x.getAmountPaid().compareTo(x.getAmountRefunded()) > 0).findFirst()
                    .orElseThrow(() -> BusinessException.of(ErrorCode.REFUND_INVALID_STATE, "There is no confirmed payment to refund"));
            Refund refund = refundService.create(order, p, r.refundAmount(), RefundSource.RETURN, "Return #" + rr.getId(), me.id(), null, true);
            rr.setRefundId(refund.getId());
        }
        return ReturnView.of(rr);
    }

    @Transactional
    public ReturnView closeReturn(AuthUser me, Long returnId) {
        ReturnRequest rr = loadReturn(returnId);
        orderAccess.requireAcceptedProvider(me, orderAccess.load(rr.getOrderId()));
        if (rr.getStatus() != ReturnStatus.RECEIVED && rr.getStatus() != ReturnStatus.REJECTED) {
            throw BusinessException.of(ErrorCode.RETURN_INVALID_STATE);
        }
        rr.setStatus(ReturnStatus.CLOSED);
        return ReturnView.of(rr);
    }

    @Transactional(readOnly = true)
    public List<ReturnView> returns(AuthUser me, Long orderId) {
        orderAccess.requireParty(me, orderAccess.load(orderId));
        return returnRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream().map(ReturnView::of).toList();
    }

    // ------------------------------------------------------------------ helpers

    private LocalDate today() {
        return LocalDate.now(clock.withZone(properties.zone()));
    }

    private WarrantyView view(ServiceWarranty w) {
        return new WarrantyView(w.getId(), w.getOrderId(), w.getProviderId(), w.getStartDate(), w.getEndDate(), w.getTerms(),
                w.getStatus(), w.isValidOn(today()));
    }

    private WarrantyClaim loadClaim(Long id) {
        return claimRepository.findById(id).orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND));
    }

    private Order orderOfClaim(WarrantyClaim c) {
        ServiceWarranty w = warrantyRepository.findById(c.getWarrantyId()).orElseThrow();
        return orderAccess.load(w.getOrderId());
    }

    private ReturnRequest loadReturn(Long id) {
        return returnRepository.findById(id).orElseThrow(() -> BusinessException.notFound(ErrorCode.RETURN_NOT_FOUND));
    }
}
