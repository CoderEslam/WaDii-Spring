package com.doubleclick.wadii.repository;

import com.doubleclick.wadii.entities.SavedOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Repository
public interface SavedOfferRepository extends JpaRepository<SavedOffer, Long> {
    List<SavedOffer> findByUserId(Long userId);
    boolean existsByUserIdAndOfferId(Long userId, Long offerId);

    @Query("SELECT so.offer.id FROM SavedOffer so WHERE so.user.id = :userId")
    Set<Long> findOfferIdsByUserId(@Param("userId") Long userId);

    @Transactional
    void deleteByUserIdAndOfferId(Long userId, Long offerId);
}
