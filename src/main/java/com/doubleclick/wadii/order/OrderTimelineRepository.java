package com.doubleclick.wadii.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderTimelineRepository extends JpaRepository<OrderTimelineEntry, Long> {

    Page<OrderTimelineEntry> findByOrderId(Long orderId, Pageable pageable);

    boolean existsByOrderIdAndEvent(Long orderId, TimelineEvent event);
}
