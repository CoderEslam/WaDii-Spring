package com.doubleclick.wadii.provider;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProviderRepository extends JpaRepository<Provider, Long>, JpaSpecificationExecutor<Provider> {

    Optional<Provider> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Provider p WHERE p.id = :id")
    Optional<Provider> findByIdForUpdate(@Param("id") Long id);

    /**
     * Eligible providers offering ALL the requested services (AND, not OR), excluding the customer's own profile.
     */
    @Query("SELECT p FROM Provider p JOIN p.user u "
            + "WHERE p.status = com.doubleclick.wadii.provider.ProviderStatus.ACTIVE "
            + "AND p.verificationStatus = com.doubleclick.wadii.provider.VerificationStatus.VERIFIED "
            + "AND p.acceptingOrders = true AND u.status = com.doubleclick.wadii.identity.AccountStatus.ACTIVE "
            + "AND u.id <> :customerId "
            + "AND (SELECT COUNT(DISTINCT s.id) FROM Provider p2 JOIN p2.services s WHERE p2.id = p.id AND s.id IN :serviceIds) = :serviceCount")
    List<Provider> findEligibleOfferingAll(@Param("serviceIds") Collection<Long> serviceIds,
                                           @Param("serviceCount") long serviceCount,
                                           @Param("customerId") Long customerId);

    @Query("SELECT p FROM Provider p WHERE p.status = com.doubleclick.wadii.provider.ProviderStatus.ACTIVE "
            + "AND p.verificationStatus = com.doubleclick.wadii.provider.VerificationStatus.VERIFIED")
    Page<Provider> findPublic(Pageable pageable);

    @Query("SELECT DISTINCT p FROM Provider p JOIN p.services s WHERE s.id = :serviceId "
            + "AND p.status = com.doubleclick.wadii.provider.ProviderStatus.ACTIVE "
            + "AND p.verificationStatus = com.doubleclick.wadii.provider.VerificationStatus.VERIFIED")
    Page<Provider> findPublicByService(@Param("serviceId") Long serviceId, Pageable pageable);
}
