package com.doubleclick.wadii.offer;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OfferRepository extends JpaRepository<Offer, Long> {

    Optional<Offer> findByRequestIdAndProviderId(Long requestId, Long providerId);

    List<Offer> findByRequestId(Long requestId);

    List<Offer> findByIdIn(Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Offer o where o.id = :id")
    Optional<Offer> lockById(@Param("id") Long id);

    @Query("select o from Offer o where o.providerId = :providerId and (:status is null or o.status = :status) order by o.id desc")
    Page<Offer> mine(@Param("providerId") Long providerId, @Param("status") Offer.Status status, Pageable pageable);

    List<Offer> findByProviderIdAndStatus(Long providerId, Offer.Status status);

    @Query("select o.id from Offer o where o.status = com.doubleclick.wadii.offer.Offer.Status.ACTIVE and o.expiresAt <= :now")
    List<Long> expiredActive(@Param("now") Instant now);

    @Query("select count(o) from Offer o where o.providerId = :providerId and o.createdAt >= :since")
    long countSubmitted(@Param("providerId") Long providerId, @Param("since") Instant since);
}
