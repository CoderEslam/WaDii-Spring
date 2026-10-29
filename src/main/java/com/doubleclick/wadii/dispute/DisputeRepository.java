package com.doubleclick.wadii.dispute;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface DisputeRepository extends JpaRepository<Dispute, Long> {

    List<Dispute> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    boolean existsByOrderIdAndTypeAndStatusIn(Long orderId, DisputeType type, Collection<DisputeStatus> statuses);

    Page<Dispute> findByCustomerId(Long customerId, Pageable pageable);

    Page<Dispute> findByProviderId(Long providerId, Pageable pageable);

    Page<Dispute> findByStatusIn(Collection<DisputeStatus> statuses, Pageable pageable);

    @Query("SELECT COUNT(d) FROM Dispute d WHERE d.openedBy = :userId AND d.createdAt >= :since")
    long countOpenedSince(@Param("userId") Long userId, @Param("since") Instant since);
}
