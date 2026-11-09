package com.doubleclick.wadii.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    Optional<Rating> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    Page<Rating> findByProviderIdAndHiddenFalseOrderByIdDesc(Long providerId, Pageable pageable);

    Page<Rating> findAllByOrderByIdDesc(Pageable pageable);

    /** [providerId, average stars, count] over visible ratings. */
    @Query("""
            select r.providerId, avg(r.stars), count(r) from Rating r
            where r.providerId in :providerIds and r.hidden = false and r.createdAt >= :since
            group by r.providerId
            """)
    List<Object[]> summary(@Param("providerIds") Collection<Long> providerIds, @Param("since") Instant since);
}
