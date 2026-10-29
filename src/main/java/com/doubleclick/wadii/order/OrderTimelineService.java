package com.doubleclick.wadii.order;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderTimelineService {

    private final OrderTimelineRepository repository;

    /** Written in the caller's transaction so the history matches what actually committed. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void add(Long orderId, TimelineEvent event, Long actorUserId, ActorRole actorRole, String details) {
        OrderTimelineEntry e = new OrderTimelineEntry();
        e.setOrderId(orderId);
        e.setEvent(event);
        e.setActorUserId(actorUserId);
        e.setActorRole(actorRole);
        e.setDetails(details == null || details.length() <= 1000 ? details : details.substring(0, 1000));
        repository.save(e);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void system(Long orderId, TimelineEvent event, String details) {
        add(orderId, event, null, ActorRole.SYSTEM, details);
    }
}
