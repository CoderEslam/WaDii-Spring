package com.doubleclick.wadii.provider;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProviderRepository extends JpaRepository<Provider, Long> {

    Optional<Provider> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Provider p where p.id = :id")
    Optional<Provider> lockById(@Param("id") Long id);

    Page<Provider> findByApplicationStatusOrderBySubmittedAtAsc(Provider.ApplicationStatus status, Pageable pageable);

    Page<Provider> findByApplicationStatusInOrderBySubmittedAtAsc(Collection<Provider.ApplicationStatus> statuses, Pageable pageable);

    @Query("""
            select p from Provider p
            where p.applicationStatus = com.doubleclick.wadii.provider.Provider.ApplicationStatus.VERIFIED
              and (:q is null or lower(p.businessName) like :q)
              and (:status is null or p.providerStatus = :status)
            order by p.id desc
            """)
    Page<Provider> searchVerified(@Param("q") String q, @Param("status") Provider.Status status, Pageable pageable);

    /** Candidates for matching: verified, active, accepting, offering one of the categories, owner account active. */
    @Query("""
            select distinct p from Provider p, ProviderCategory pc, com.doubleclick.wadii.identity.User u
            where pc.providerId = p.id and u.id = p.userId
              and pc.categoryId in :categoryIds
              and p.applicationStatus = com.doubleclick.wadii.provider.Provider.ApplicationStatus.VERIFIED
              and p.providerStatus = com.doubleclick.wadii.provider.Provider.Status.ACTIVE
              and p.acceptingOrders = true
              and u.status = com.doubleclick.wadii.identity.AccountStatus.ACTIVE
            """)
    List<Provider> matchingCandidates(@Param("categoryIds") Collection<Long> categoryIds);

    long countByApplicationStatus(Provider.ApplicationStatus status);

    long countByApplicationStatusIn(Collection<Provider.ApplicationStatus> statuses);
}
