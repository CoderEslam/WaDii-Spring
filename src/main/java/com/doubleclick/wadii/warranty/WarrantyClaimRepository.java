package com.doubleclick.wadii.warranty;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;

public interface WarrantyClaimRepository extends JpaRepository<WarrantyClaim, Long> {

    Page<WarrantyClaim> findByCustomerIdOrderByIdDesc(Long customerId, Pageable pageable);

    Page<WarrantyClaim> findByProviderIdOrderByIdDesc(Long providerId, Pageable pageable);

    @Query("select c from WarrantyClaim c where (:status is null or c.status = :status) order by c.id desc")
    Page<WarrantyClaim> search(@Param("status") WarrantyClaim.Status status, Pageable pageable);

    boolean existsByWarrantyIdAndStatusIn(Long warrantyId, Collection<WarrantyClaim.Status> statuses);

    long countByStatusIn(Collection<WarrantyClaim.Status> statuses);

    long countByProviderIdAndCreatedAtAfter(Long providerId, Instant since);
}
