package com.doubleclick.wadii.provider;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface ProviderOfferRepository extends JpaRepository<ProviderOffer, Long> {

    @Query("SELECT o FROM ProviderOffer o WHERE o.endDate >= :today")
    Page<ProviderOffer> findCurrent(@Param("today") LocalDate today, Pageable pageable);

    @Query("SELECT DISTINCT o FROM ProviderOffer o JOIN o.services s WHERE s.id = :serviceId AND o.endDate >= :today")
    Page<ProviderOffer> findCurrentByService(@Param("serviceId") Long serviceId, @Param("today") LocalDate today, Pageable pageable);

    Page<ProviderOffer> findByProviderId(Long providerId, Pageable pageable);
}
