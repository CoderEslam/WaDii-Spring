package com.doubleclick.wadii.provider;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface VehicleSupportRepository extends JpaRepository<VehicleSupport, Long> {

    List<VehicleSupport> findByProviderIdOrderByIdAsc(Long providerId);

    List<VehicleSupport> findByProviderIdIn(Collection<Long> providerIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from VehicleSupport v where v.providerId = :providerId")
    void deleteByProviderId(@Param("providerId") Long providerId);
}
