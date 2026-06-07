package com.doubleclick.wadii.repository;

import com.doubleclick.wadii.entities.SavedOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface SavedOfferRepository extends JpaRepository<SavedOffer, Long> {
    List<SavedOffer> findByUserId(Long userId);
    boolean existsByUserIdAndOfferId(Long userId, Long offerId);

    @Transactional
    void deleteByUserIdAndOfferId(Long userId, Long offerId);
}
