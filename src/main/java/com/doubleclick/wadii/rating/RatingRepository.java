package com.doubleclick.wadii.rating;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    boolean existsByOrderIdAndCustomerId(Long orderId, Long customerId);

    Optional<Rating> findByOrderId(Long orderId);

    Page<Rating> findByProviderId(Long providerId, Pageable pageable);

    @Query("SELECT AVG(r.score) FROM Rating r WHERE r.providerId = :providerId")
    Double averageScore(@Param("providerId") Long providerId);

    long countByProviderId(Long providerId);
}
