package com.doubleclick.wadii.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            select a from AuditLog a
            where (:entityType is null or a.entityType = :entityType) and (:entityId is null or a.entityId = :entityId)
            """)
    Page<AuditLog> search(@Param("entityType") String entityType, @Param("entityId") Long entityId, Pageable pageable);
}
