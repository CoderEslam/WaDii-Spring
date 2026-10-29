package com.doubleclick.wadii.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface FraudFlagRepository extends JpaRepository<FraudFlag, Long> {

    Page<FraudFlag> findByStatus(FraudFlagStatus status, Pageable pageable);

    @Query("SELECT COUNT(f) > 0 FROM FraudFlag f WHERE f.subjectUserId = :userId AND f.type = :type "
            + "AND f.status = com.doubleclick.wadii.audit.FraudFlagStatus.OPEN AND f.createdAt >= :since")
    boolean existsOpenSince(@Param("userId") Long userId, @Param("type") FraudFlagType type, @Param("since") Instant since);
}
