package com.doubleclick.wadii.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.actorUserId = :userId AND a.action = :action AND a.createdAt >= :since")
    long countActionsSince(@Param("userId") Long userId, @Param("action") AuditAction action, @Param("since") Instant since);
}
