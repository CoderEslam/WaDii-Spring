package com.doubleclick.wadii.repository;

import com.doubleclick.wadii.entities.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProviderRepository extends JpaRepository<Provider, Long> {

    Optional<Provider> findByUserId(Long userId);

    @Query("SELECT DISTINCT p FROM Provider p JOIN p.services s WHERE s.id = :serviceId")
    List<Provider> findAllByServiceId(@Param("serviceId") Long serviceId);

    @Query("SELECT DISTINCT p FROM Provider p JOIN p.services s WHERE s.id IN :serviceIds")
    List<Provider> findAllByServiceIdIn(@Param("serviceIds") List<Long> serviceIds);

    List<Provider> findByNameContainingIgnoreCase(String q);

    /** Active providers accepting orders that offer ALL of the requested services (excluding the order owner). */
    @Query("SELECT p FROM Provider p WHERE (p.active IS NULL OR p.active = true) "
            + "AND (p.acceptingOrders IS NULL OR p.acceptingOrders = true) AND p.user.id <> :ownerId "
            + "AND (SELECT COUNT(DISTINCT s.id) FROM Service s JOIN s.providers sp WHERE sp.id = p.id AND s.id IN :serviceIds) = :serviceCount")
    List<Provider> findEligibleForAllServices(@Param("serviceIds") Collection<Long> serviceIds,
                                              @Param("serviceCount") long serviceCount,
                                              @Param("ownerId") Long ownerId);
}
