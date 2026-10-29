package com.doubleclick.wadii.payment;

import com.doubleclick.wadii.audit.AuditAction;
import com.doubleclick.wadii.audit.AuditService;
import com.doubleclick.wadii.audit.FraudFlagType;
import com.doubleclick.wadii.audit.FraudService;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.notification.NotificationService;
import com.doubleclick.wadii.notification.NotificationType;
import com.doubleclick.wadii.order.ActorRole;
import com.doubleclick.wadii.order.Order;
import com.doubleclick.wadii.order.OrderAccess;
import com.doubleclick.wadii.order.OrderStatus;
import com.doubleclick.wadii.order.OrderTimelineService;
import com.doubleclick.wadii.order.TimelineEvent;
import com.doubleclick.wadii.payment.dto.PaymentDtos.RefundRequest;
import com.doubleclick.wadii.payment.dto.PaymentDtos.RefundView;
import com.doubleclick.wadii.policy.PolicyService;
import com.doubleclick.wadii.policy.PolicyType;
import com.doubleclick.wadii.provider.ProviderAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Refunds (money returned), separate from product returns. Partial refunds allowed up to
 * (paid - refunds already requested/approved/completed) per payment.
 * Because V1 payments are manual transfers to the provider, the provider (or a FINANCE admin) approves the refund
 * and marks it COMPLETED once the money was sent back.
 */
@Service
@RequiredArgsConstructor
public class RefundService {

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final OrderAccess orderAccess;
    private final ProviderAccess providerAccess;
    private final PolicyService policyService;
    private final NotificationService notifications;
    private final AuditService audit;
    private final FraudService fraud;
    private final OrderTimelineService timeline;
    private final Clock clock;

    @Transactional
    public RefundView request(AuthUser me, Long orderId, RefundRequest request) {
        Order order = orderAccess.loadForUpdate(orderId);
        orderAccess.requireCustomer(me, order);
        Payment payment = pickPayment(order, request.paymentId());
        if (order.getStatus() == OrderStatus.COMPLETED && order.getCompletedAt() != null) {
            Integer days = policyService.snapshotTerms(order.getId(), PolicyType.REFUND).periodDays();
            if (days != null && clock.instant().isAfter(order.getCompletedAt().plus(Duration.ofDays(days)))) {
                throw BusinessException.of(ErrorCode.REFUND_INVALID_STATE, "The refund window of " + days + " days is over");
            }
        }
        Refund refund = create(order, payment, request.amount(), RefundSource.CUSTOMER_REQUEST, request.reason(), me.id(), null, false);
        Instant since = clock.instant().minus(Duration.ofDays(30));
        if (refundRepository.countRequestedSince(me.id(), since) >= 3) {
            fraud.flag(me.id(), FraudFlagType.SUSPICIOUS_REFUNDS, "REFUND", refund.getId(), "3+ refund requests in 30 days");
        }
        return RefundView.of(refund);
    }

    /** System / admin initiated refund (cancellation, no-show, dispute). {@code approved} skips the provider decision. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Refund create(Order order, Payment payment, BigDecimal amount, RefundSource source, String reason,
                         Long requestedBy, Long disputeId, boolean approved) {
        if (payment.getAmountPaid().signum() <= 0) {
            throw BusinessException.of(ErrorCode.REFUND_INVALID_STATE, "Only a confirmed payment can be refunded");
        }
        BigDecimal refundable = payment.getAmountPaid().subtract(refundRepository.sumCommitted(payment.getId()));
        if (amount.signum() <= 0 || amount.compareTo(refundable) > 0) {
            throw BusinessException.of(ErrorCode.REFUND_AMOUNT_INVALID, "At most " + refundable.max(BigDecimal.ZERO) + " can be refunded");
        }
        Refund r = new Refund();
        r.setPaymentId(payment.getId());
        r.setOrderId(order.getId());
        r.setAmount(amount);
        r.setCurrency(payment.getCurrency());
        r.setReason(reason);
        r.setSource(source);
        r.setRequestedBy(requestedBy);
        r.setDisputeId(disputeId);
        if (approved) {
            r.setStatus(RefundStatus.APPROVED);
            r.setApprovedAt(clock.instant());
        }
        r = refundRepository.save(r);
        audit.system(AuditAction.REFUND_CREATED, "REFUND", r.getId(), source + " " + amount + " on payment " + payment.getId());
        timeline.system(order.getId(), TimelineEvent.REFUND_REQUESTED, amount + " " + payment.getCurrency() + " (" + source + ")");
        notifications.notify(order.getAcceptedProvider().getUser().getId(), NotificationType.REFUND_REQUESTED, "Refund " + (approved ? "approved" : "requested"),
                "A refund of " + amount + " " + payment.getCurrency() + " for order #" + order.getId() + " needs your action.", order.getId());
        if (requestedBy == null || !requestedBy.equals(order.getCustomer().getId())) {
            notifications.notify(order.getCustomer().getId(), NotificationType.REFUND_REQUESTED, "Refund created",
                    "A refund of " + amount + " " + payment.getCurrency() + " was created for order #" + order.getId() + ".", order.getId());
        }
        return r;
    }

    @Transactional
    public RefundView decide(AuthUser me, Long refundId, boolean approve, String note) {
        Refund r = load(refundId);
        Order order = orderAccess.loadForUpdate(r.getOrderId());
        ActorRole role = requireDecider(me, order);
        if (r.getStatus() != RefundStatus.REQUESTED) throw BusinessException.of(ErrorCode.REFUND_INVALID_STATE);
        r.setStatus(approve ? RefundStatus.APPROVED : RefundStatus.REJECTED);
        r.setApprovedBy(me.id());
        r.setApprovedAt(clock.instant());
        r.setDecisionNote(note);
        audit.record(me, role, AuditAction.REFUND_DECIDED, "REFUND", r.getId(), (approve ? "approved" : "rejected") + (note == null ? "" : ": " + note));
        timeline.add(order.getId(), approve ? TimelineEvent.REFUND_APPROVED : TimelineEvent.REFUND_REJECTED, me.id(), role, r.getAmount().toPlainString());
        notifications.notify(order.getCustomer().getId(), NotificationType.REFUND_UPDATED, approve ? "Refund approved" : "Refund rejected",
                "Your refund of " + r.getAmount() + " " + r.getCurrency() + " for order #" + order.getId() + " was "
                        + (approve ? "approved." : "rejected." + (note == null ? "" : " " + note) + " You can open a dispute."), order.getId());
        return RefundView.of(r);
    }

    /** The money was sent back. Updates the payment's refunded amount; fully refunded payments become REFUNDED. */
    @Transactional
    public RefundView complete(AuthUser me, Long refundId) {
        Refund r = load(refundId);
        Order order = orderAccess.loadForUpdate(r.getOrderId());
        ActorRole role = requireDecider(me, order);
        if (r.getStatus() != RefundStatus.APPROVED) throw BusinessException.of(ErrorCode.REFUND_INVALID_STATE);
        Payment p = paymentRepository.findByIdForUpdate(r.getPaymentId()).orElseThrow();
        p.setAmountRefunded(p.getAmountRefunded().add(r.getAmount()));
        if (p.getAmountRefunded().compareTo(p.getAmountPaid()) >= 0) {
            p.setStatus(PaymentStatus.REFUNDED);
        }
        r.setStatus(RefundStatus.COMPLETED);
        r.setProcessedAt(clock.instant());
        audit.record(me, role, AuditAction.REFUND_COMPLETED, "REFUND", r.getId(), r.getAmount().toPlainString());
        audit.record(me, role, AuditAction.PAYMENT_REFUNDED, "PAYMENT", p.getId(), r.getAmount().toPlainString());
        timeline.add(order.getId(), TimelineEvent.REFUND_COMPLETED, me.id(), role, r.getAmount() + " " + r.getCurrency());
        notifications.notify(order.getCustomer().getId(), NotificationType.REFUND_UPDATED, "Refund sent",
                "The refund of " + r.getAmount() + " " + r.getCurrency() + " for order #" + order.getId() + " was sent.", order.getId());
        return RefundView.of(r);
    }

    @Transactional(readOnly = true)
    public RefundView get(AuthUser me, Long refundId) {
        Refund r = load(refundId);
        orderAccess.requireParty(me, orderAccess.load(r.getOrderId()));
        return RefundView.of(r);
    }

    @Transactional(readOnly = true)
    public PageResponse<RefundView> adminList(RefundStatus status, Integer page, Integer size) {
        var pageable = PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = status == null ? refundRepository.findAll(pageable) : refundRepository.findByStatus(status, pageable);
        return PageResponse.of(result, RefundView::of);
    }

    /** The accepted provider (holder of the money) or a FINANCE admin. */
    private ActorRole requireDecider(AuthUser me, Order order) {
        if (me.isFinance()) return ActorRole.ADMIN;
        orderAccess.requireAcceptedProvider(me, order);
        return ActorRole.PROVIDER;
    }

    private Payment pickPayment(Order order, Long paymentId) {
        Optional<Payment> p = paymentId != null
                ? paymentRepository.findById(paymentId).filter(x -> x.getOrderId().equals(order.getId()))
                : paymentRepository.findByOrderIdOrderByCreatedAtAsc(order.getId()).stream()
                .filter(x -> x.getAmountPaid().compareTo(x.getAmountRefunded()) > 0).findFirst();
        return p.orElseThrow(() -> BusinessException.of(ErrorCode.REFUND_INVALID_STATE, "There is no confirmed payment to refund"));
    }

    private Refund load(Long id) {
        return refundRepository.findById(id).orElseThrow(() -> BusinessException.notFound(ErrorCode.REFUND_NOT_FOUND));
    }
}
