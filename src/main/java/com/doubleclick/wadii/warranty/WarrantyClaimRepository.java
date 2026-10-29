package com.doubleclick.wadii.warranty;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WarrantyClaimRepository extends JpaRepository<WarrantyClaim, Long> {

    List<WarrantyClaim> findByWarrantyIdOrderByCreatedAtDesc(Long warrantyId);
}
