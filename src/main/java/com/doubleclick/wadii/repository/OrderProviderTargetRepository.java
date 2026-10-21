package com.doubleclick.wadii.repository;

import com.doubleclick.wadii.entities.Order;
import com.doubleclick.wadii.entities.OrderProviderTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderProviderTargetRepository extends JpaRepository<OrderProviderTarget, Long> {

    Optional<OrderProviderTarget> findByOrderIdAndProviderId(Long orderId, Long providerId);

    boolean existsByOrderIdAndProviderId(Long orderId, Long providerId);

    List<OrderProviderTarget> findByOrderId(Long orderId);

    @Query("SELECT t.order FROM OrderProviderTarget t WHERE t.provider.id = :providerId ORDER BY t.order.id DESC")
    List<Order> findOrdersByProviderId(@Param("providerId") Long providerId);
}
