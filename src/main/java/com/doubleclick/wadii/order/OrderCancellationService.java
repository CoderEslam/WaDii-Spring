package com.doubleclick.wadii.order;

import com.doubleclick.wadii.appointment.Appointment;
import com.doubleclick.wadii.appointment.AppointmentChange;
import com.doubleclick.wadii.appointment.AppointmentChangeRepository;
import com.doubleclick.wadii.appointment.AppointmentRepository;
import com.doubleclick.wadii.appointment.AppointmentStatus;
import com.doubleclick.wadii.audit.AuditAction;
import com.doubleclick.wadii.audit.AuditService;
import com.doubleclick.wadii.catalog.CancellationReason;
import com.doubleclick.wadii.catalog.CancellationReasonRepository;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.notification.NotificationService;
import com.doubleclick.wadii.notification.NotificationType;
import com.doubleclick.wadii.payment.Payment;
import com.doubleclick.wadii.payment.PaymentService;
import com.doubleclick.wadii.payment.Refund;
import com.doubleclick.wadii.payment.RefundRepository;
import com.doubleclick.wadii.payment.RefundService;
import com.doubleclick.wadii.payment.RefundSource;
import com.doubleclick.wadii.response.OrderResponse;
import com.doubleclick.wadii.response.OrderResponseRepository;
import com.doubleclick.wadii.response.ResponseStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The single implementation of order cancellation (customer, provider, admin, system / no-show).
 * Runs inside the caller's transaction with the order row already locked.
 */
@Service
@RequiredArgsConstructor
public class OrderCancellationService {

    private final OrderCancellationRepository cancellationRepository;
    private final CancellationReasonRepository reasonRepository;
    private final OrderResponseRepository responseRepository;
    private final OrderProviderTargetRepository targetRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentChangeRepository appointmentChangeRepository;
    private final RefundRepository refundRepository;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final NotificationService notifications;
    private final AuditService audit;
    private final OrderTimelineService timeline;
    private final Clock clock;

    /** How much of each confirmed payment goes back to the customer. */
    public record RefundRule(boolean refund, BigDecimal feePercent) {
        public static RefundRule full() {
            return new RefundRule(true, BigDecimal.ZERO);
        }

        public static RefundRule none() {
            return new RefundRule(false, BigDecimal.ZERO);
        }

        public static RefundRule minusFee(BigDecimal feePercent) {
            return new RefundRule(true, feePercent);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Long> cancel(Order order, AuthUser actor, ActorRole role, Long reasonId, String reasonText,
                             RefundRule refundRule, RefundSource refundSource) {
        Instant now = clock.instant();
        OrderStatus before = order.getStatus();
        CancellationReason reason = reasonId == null ? null
                : reasonRepository.findById(reasonId).orElseThrow(() -> BusinessException.validation("reasonId does not exist"));
        OrderStateMachine.transition(order, OrderStatus.CANCELED, now);

        OrderCancellation c = new OrderCancellation();
        c.setOrderId(order.getId());
        c.setCanceledBy(actor == null ? null : actor.id());
        c.setActorRole(role);
        c.setReason(reason);
        c.setReasonText(reasonText);
        c.setOrderStatusAtCancel(before);
        cancellationRepository.save(c);

        for (OrderResponse r : responseRepository.findByOrderIdAndStatus(order.getId(), ResponseStatus.PENDING)) {
            r.changeStatus(ResponseStatus.CANCELED, now);
            notifications.notify(r.getProvider().getUser().getId(), NotificationType.ORDER_CANCELED, "Order canceled",
                    "Order #" + order.getId() + " was canceled; your offer is closed.", order.getId());
        }
        targetRepository.expireOpenTargets(order.getId(), now);

        appointmentRepository.findByOrderId(order.getId()).filter(a -> a.getStatus().isOpen()).ifPresent(a -> cancelAppointment(a, actor, reasonText));

        paymentService.cancelActive(order);
        List<Long> refundIds = new ArrayList<>();
        if (refundRule.refund()) {
            for (Payment p : paymentService.confirmedPayments(order.getId())) {
                BigDecimal committed = refundRepository.findByPaymentId(p.getId()).stream()
                        .filter(r -> r.getStatus().isCommitted()).map(Refund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal refundable = p.getAmountPaid().subtract(committed);
                BigDecimal fee = refundable.multiply(refundRule.feePercent()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                BigDecimal amount = refundable.subtract(fee);
                if (amount.signum() > 0) {
                    boolean approved = role != ActorRole.CUSTOMER; // provider / admin / system cancellations owe the refund outright
                    refundIds.add(refundService.create(order, p, amount, refundSource,
                            "Order canceled (" + role + ")" + (fee.signum() > 0 ? ", fee " + fee : ""),
                            actor == null ? null : actor.id(), null, approved).getId());
                }
            }
        }

        String details = role + (reasonText == null ? "" : ": " + reasonText) + (reason == null ? "" : " [" + reason.getText() + "]");
        timeline.add(order.getId(), TimelineEvent.ORDER_CANCELED, actor == null ? null : actor.id(), role, details);
        if (actor == null) {
            audit.system(AuditAction.ORDER_CANCELED, "ORDER", order.getId(), details);
        } else {
            audit.record(actor, role, AuditAction.ORDER_CANCELED, "ORDER", order.getId(), details);
        }
        if (role != ActorRole.CUSTOMER) {
            notifications.notify(order.getCustomer().getId(), NotificationType.ORDER_CANCELED, "Order canceled",
                    "Order #" + order.getId() + " was canceled" + (reasonText == null ? "." : ": " + reasonText), order.getId());
        }
        if (order.getAcceptedProvider() != null && role != ActorRole.PROVIDER) {
            notifications.notify(order.getAcceptedProvider().getUser().getId(), NotificationType.ORDER_CANCELED, "Order canceled",
                    "Order #" + order.getId() + " was canceled" + (reasonText == null ? "." : ": " + reasonText), order.getId());
        }
        return refundIds;
    }

    private void cancelAppointment(Appointment a, AuthUser actor, String reason) {
        AppointmentStatus old = a.getStatus();
        a.setStatus(AppointmentStatus.CANCELED);
        AppointmentChange change = new AppointmentChange();
        change.setAppointmentId(a.getId());
        change.setOldStart(a.getScheduledStart());
        change.setNewStart(a.getScheduledStart());
        change.setOldStatus(old);
        change.setNewStatus(AppointmentStatus.CANCELED);
        change.setChangedBy(actor == null ? null : actor.id());
        change.setReason(reason == null ? "order canceled" : reason);
        appointmentChangeRepository.save(change);
    }
}
