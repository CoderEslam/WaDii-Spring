package com.doubleclick.wadii.order;

import com.doubleclick.wadii.audit.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Writes an order timeline entry and the matching audit record together. */
@Component
@RequiredArgsConstructor
public class OrderTimeline {

    private final OrderEventRepository events;
    private final AuditService audit;

    @Transactional
    public void record(ServiceOrder order, String type, Long actorUserId, OrderEvent.ActorRole role, String note) {
        events.save(new OrderEvent(order.getId(), type, actorUserId, role, note));
        audit.record("ORDER_" + type, "ORDER", order.getId(), note);
    }
}
