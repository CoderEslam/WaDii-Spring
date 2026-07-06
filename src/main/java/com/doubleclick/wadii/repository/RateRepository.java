package com.doubleclick.wadii.repository;

import com.doubleclick.wadii.entities.Rate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RateRepository extends JpaRepository<Rate, Long> {

    @Query("SELECT AVG(r.rate) FROM Rate r WHERE r.provider.id = :providerId")
    Double findAverageRateByProviderId(@Param("providerId") Long providerId);

    Optional<Rate> findByUserIdAndProviderId(Long userId, Long providerId);

}
