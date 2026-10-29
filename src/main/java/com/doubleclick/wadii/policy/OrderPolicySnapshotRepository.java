package com.doubleclick.wadii.policy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderPolicySnapshotRepository extends JpaRepository<OrderPolicySnapshot, Long> {

    List<OrderPolicySnapshot> findByOrderIdOrderByPolicyType(Long orderId);

    Optional<OrderPolicySnapshot> findByOrderIdAndPolicyType(Long orderId, PolicyType policyType);

    boolean existsByOrderId(Long orderId);
}
