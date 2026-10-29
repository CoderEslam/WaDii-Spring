package com.doubleclick.wadii.payment;

import com.doubleclick.wadii.audit.AuditAction;
import com.doubleclick.wadii.audit.AuditService;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.notification.NotificationService;
import com.doubleclick.wadii.notification.NotificationType;
import com.doubleclick.wadii.order.ActorRole;
import com.doubleclick.wadii.order.Order;
import com.doubleclick.wadii.order.OrderAccess;
import com.doubleclick.wadii.order.OrderTimelineService;
import com.doubleclick.wadii.order.TimelineEvent;
import com.doubleclick.wadii.payment.dto.PaymentDtos.ChargeRequest;
import com.doubleclick.wadii.payment.dto.PaymentDtos.ChargeView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/** The accepted price is never edited; extra work is an explicit charge the customer approves or rejects. */
@Service
@RequiredArgsConstructor
public class AdditionalChargeService {

    private final AdditionalChargeRepository chargeRepository;
    private final OrderAccess orderAccess;
    private final PaymentService paymentService;
    private final NotificationService notifications;
    private final AuditService audit;
    private final OrderTimelineService timeline;
    private final Clock clock;

    @Transactional
    public ChargeView request(AuthUser me, Long orderId, ChargeRequest request) {
        Order order = orderAccess.loadForUpdate(orderId);
        orderAccess.requireAcceptedProvider(me, order);
        if (!order.getStatus().isAssigned()) throw BusinessException.of(ErrorCode.ORDER_INVALID_STATE);
        AdditionalCharge c = new AdditionalCharge();
        c.setOrderId(order.getId());
        c.setAmount(request.amount());
        c.setCurrency(order.getCurrency());
        c.setReason(request.reason().trim());
        c.setRequestedBy(me.id());
        c = chargeRepository.save(c);
        audit.record(me, ActorRole.PROVIDER, AuditAction.ADDITIONAL_CHARGE_REQUESTED, "ADDITIONAL_CHARGE", c.getId(), request.amount().toPlainString());
        timeline.add(order.getId(), TimelineEvent.ADDITIONAL_CHARGE_REQUESTED, me.id(), ActorRole.PROVIDER, request.amount() + ": " + request.reason());
        notifications.notify(order.getCustomer().getId(), NotificationType.ADDITIONAL_CHARGE_REQUESTED, "Additional charge requested",
                "The provider asks for " + request.amount() + " " + order.getCurrency() + " more on order #" + order.getId() + ": " + request.reason(), order.getId());
        return ChargeView.of(c);
    }

    /** Customer decision. Approval raises the amount due and opens a payment for the difference. */
    @Transactional
    public ChargeView decide(AuthUser me, Long chargeId, boolean approve) {
        AdditionalCharge c = chargeRepository.findById(chargeId).orElseThrow(() -> BusinessException.notFound(ErrorCode.CHARGE_NOT_FOUND));
        Order order = orderAccess.loadForUpdate(c.getOrderId());
        orderAccess.requireCustomer(me, order);
        if (c.getStatus() != ChargeStatus.REQUESTED) throw BusinessException.of(ErrorCode.CHARGE_INVALID_STATE);
        if (!order.getStatus().isAssigned()) throw BusinessException.of(ErrorCode.ORDER_INVALID_STATE);
        c.setStatus(approve ? ChargeStatus.APPROVED : ChargeStatus.REJECTED);
        c.setDecidedBy(me.id());
        c.setDecidedAt(clock.instant());
        chargeRepository.flush();
        audit.record(me, ActorRole.CUSTOMER, AuditAction.ADDITIONAL_CHARGE_DECIDED, "ADDITIONAL_CHARGE", c.getId(), c.getStatus().name());
        timeline.add(order.getId(), approve ? TimelineEvent.ADDITIONAL_CHARGE_APPROVED : TimelineEvent.ADDITIONAL_CHARGE_REJECTED,
                me.id(), ActorRole.CUSTOMER, c.getAmount().toPlainString());
        notifications.notify(order.getAcceptedProvider().getUser().getId(), NotificationType.ADDITIONAL_CHARGE_DECIDED,
                approve ? "Additional charge approved" : "Additional charge rejected",
                "The customer " + (approve ? "approved" : "rejected") + " the charge of " + c.getAmount() + " " + c.getCurrency()
                        + " on order #" + order.getId() + ".", order.getId());
        if (approve) {
            paymentService.openForOutstanding(order);
        }
        return ChargeView.of(c);
    }

    /** The provider withdraws a charge the customer has not decided on yet. */
    @Transactional
    public ChargeView cancel(AuthUser me, Long chargeId) {
        AdditionalCharge c = chargeRepository.findById(chargeId).orElseThrow(() -> BusinessException.notFound(ErrorCode.CHARGE_NOT_FOUND));
        Order order = orderAccess.loadForUpdate(c.getOrderId());
        orderAccess.requireAcceptedProvider(me, order);
        if (c.getStatus() != ChargeStatus.REQUESTED) throw BusinessException.of(ErrorCode.CHARGE_INVALID_STATE);
        c.setStatus(ChargeStatus.CANCELED);
        c.setDecidedBy(me.id());
        c.setDecidedAt(clock.instant());
        return ChargeView.of(c);
    }

    @Transactional(readOnly = true)
    public List<ChargeView> list(AuthUser me, Long orderId) {
        Order order = orderAccess.load(orderId);
        orderAccess.requireParty(me, order);
        return chargeRepository.findByOrderIdOrderByCreatedAtAsc(orderId).stream().map(ChargeView::of).toList();
    }
}
