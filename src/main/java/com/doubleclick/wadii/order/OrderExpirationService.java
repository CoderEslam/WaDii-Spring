package com.doubleclick.wadii.order;

import com.doubleclick.wadii.audit.AuditAction;
import com.doubleclick.wadii.audit.AuditService;
import com.doubleclick.wadii.notification.NotificationService;
import com.doubleclick.wadii.notification.NotificationType;
import com.doubleclick.wadii.payment.PaymentService;
import com.doubleclick.wadii.response.OrderResponse;
import com.doubleclick.wadii.response.OrderResponseRepository;
import com.doubleclick.wadii.response.ResponseStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/** Order response deadline: no new offers, pending offers expire, and the order becomes EXPIRED if nothing was accepted. */
@Service
@RequiredArgsConstructor
public class OrderExpirationService {

    private final OrderAccess orderAccess;
    private final OrderResponseRepository responseRepository;
    private final OrderProviderTargetRepository targetRepository;
    private final PaymentService paymentService;
    private final NotificationService notifications;
    private final AuditService audit;
    private final OrderTimelineService timeline;
    private final Clock clock;

    @Transactional
    public void expire(Long orderId) {
        Order order = orderAccess.loadForUpdate(orderId);
        Instant now = clock.instant();
        if (!order.getStatus().awaitingOffers() || now.isBefore(order.getResponseDeadline())) return;
        for (OrderResponse r : responseRepository.findByOrderIdAndStatus(orderId, ResponseStatus.PENDING)) {
            r.changeStatus(ResponseStatus.EXPIRED, now);
            audit.system(AuditAction.RESPONSE_EXPIRED, "RESPONSE", r.getId(), "order deadline");
            notifications.notify(r.getProvider().getUser().getId(), NotificationType.RESPONSE_EXPIRED, "Offer expired",
                    "Order #" + orderId + " reached its response deadline; your offer expired.", orderId);
        }
        targetRepository.expireOpenTargets(orderId, now);
        OrderStateMachine.transition(order, OrderStatus.EXPIRED, now);
        paymentService.cancelActive(order);
        timeline.system(orderId, TimelineEvent.ORDER_EXPIRED, "response deadline passed without an accepted offer");
        audit.system(AuditAction.ORDER_EXPIRED, "ORDER", orderId, null);
        notifications.notify(order.getCustomer().getId(), NotificationType.ORDER_EXPIRED, "Order expired",
                "Order #" + orderId + " expired without an accepted offer. You can create a new order.", orderId);
    }
}
