package com.doubleclick.wadii.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderChargeRepository extends JpaRepository<OrderCharge, Long> {

    List<OrderCharge> findByOrderIdOrderByIdAsc(Long orderId);

    Optional<OrderCharge> findByIdAndOrderId(Long id, Long orderId);
}
