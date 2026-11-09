package com.doubleclick.wadii.warranty;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WarrantyRepository extends JpaRepository<Warranty, Long> {

    Optional<Warranty> findByOrderId(Long orderId);

    List<Warranty> findByCustomerIdOrderByIdDesc(Long customerId);

    List<Warranty> findByProviderIdOrderByIdDesc(Long providerId);
}
