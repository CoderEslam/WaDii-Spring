package com.doubleclick.wadii.response;

import com.doubleclick.wadii.order.Order;
import com.doubleclick.wadii.order.OrderProviderTarget;
import com.doubleclick.wadii.order.OrderProviderTargetRepository;
import com.doubleclick.wadii.order.OrderRepository;
import com.doubleclick.wadii.order.OrderStateMachine;
import com.doubleclick.wadii.order.OrderStatus;
import com.doubleclick.wadii.order.OrderTimelineService;
import com.doubleclick.wadii.order.TargetStatus;
import com.doubleclick.wadii.order.TimelineEvent;
import com.doubleclick.wadii.provider.ProviderService.ProviderStatusChanged;
import com.doubleclick.wadii.provider.ProviderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;

/**
 * When a provider is suspended or banned: pending offers are withdrawn (CANCELED, kept as history) and open
 * invitations declined, inside the admin's transaction. Orders already accepted are left untouched and counted,
 * so support handles each one explicitly.
 */
@Component
@RequiredArgsConstructor
public class ProviderSuspensionHandler {

    private final OrderResponseRepository responseRepository;
    private final OrderProviderTargetRepository targetRepository;
    private final OrderRepository orderRepository;
    private final OrderTimelineService timeline;
    private final Clock clock;

    @EventListener
    public void onStatusChanged(ProviderStatusChanged event) {
        if (event.status == ProviderStatus.ACTIVE) return;
        Instant now = clock.instant();
        for (OrderResponse r : responseRepository.findByProviderIdAndStatus(event.providerId, ResponseStatus.PENDING)) {
            Order order = orderRepository.findByIdForUpdate(r.getOrder().getId()).orElseThrow();
            r.changeStatus(ResponseStatus.CANCELED, now);
            timeline.system(order.getId(), TimelineEvent.OFFER_WITHDRAWN, "provider " + event.status.name().toLowerCase());
            if (order.getStatus() == OrderStatus.RESPONSES_AVAILABLE
                    && responseRepository.countByOrderIdAndStatus(order.getId(), ResponseStatus.PENDING) == 0) {
                OrderStateMachine.transition(order, OrderStatus.QUOTING, now);
            }
            event.withdrawnOffers++;
        }
        for (OrderProviderTarget t : targetRepository.findByProviderIdAndStatusIn(event.providerId, EnumSet.of(TargetStatus.TARGETED, TargetStatus.VIEWED))) {
            t.setStatus(TargetStatus.DECLINED);
        }
        event.assignedOrders = orderRepository.countByAcceptedProviderIdAndStatusIn(event.providerId,
                EnumSet.of(OrderStatus.ACCEPTED, OrderStatus.IN_PROGRESS));
    }
}
