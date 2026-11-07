package com.doubleclick.wadii.subscription;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProviderSubscriptionRepository extends JpaRepository<ProviderSubscription, Long> {

    Optional<ProviderSubscription> findByProviderId(Long providerId);

    List<ProviderSubscription> findByProviderIdIn(Collection<Long> providerIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ProviderSubscription s where s.providerId = :providerId")
    Optional<ProviderSubscription> lockByProviderId(@Param("providerId") Long providerId);
}
