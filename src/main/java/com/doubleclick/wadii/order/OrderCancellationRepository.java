package com.doubleclick.wadii.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderCancellationRepository extends JpaRepository<OrderCancellation, Long> {

    Optional<OrderCancellation> findByOrderId(Long orderId);
}
