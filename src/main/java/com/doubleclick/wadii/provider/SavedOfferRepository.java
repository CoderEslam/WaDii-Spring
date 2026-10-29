package com.doubleclick.wadii.provider;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface SavedOfferRepository extends JpaRepository<SavedOffer, Long> {

    boolean existsByUserIdAndOfferId(Long userId, Long offerId);

    Optional<SavedOffer> findByUserIdAndOfferId(Long userId, Long offerId);

    Page<SavedOffer> findByUserId(Long userId, Pageable pageable);

    @Query("SELECT s.offer.id FROM SavedOffer s WHERE s.userId = :userId AND s.offer.id IN :offerIds")
    Set<Long> findSavedOfferIds(@Param("userId") Long userId, @Param("offerIds") Collection<Long> offerIds);
}
