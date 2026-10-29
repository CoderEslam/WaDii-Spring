package com.doubleclick.wadii.warranty;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface ServiceWarrantyRepository extends JpaRepository<ServiceWarranty, Long> {

    Optional<ServiceWarranty> findByOrderId(Long orderId);

    Page<ServiceWarranty> findByCustomerId(Long customerId, Pageable pageable);

    Page<ServiceWarranty> findByProviderId(Long providerId, Pageable pageable);

    @Modifying
    @Query("UPDATE ServiceWarranty w SET w.status = com.doubleclick.wadii.warranty.WarrantyStatus.EXPIRED "
            + "WHERE w.status = com.doubleclick.wadii.warranty.WarrantyStatus.ACTIVE AND w.endDate < :today")
    int expireEndedBefore(@Param("today") LocalDate today);
}
