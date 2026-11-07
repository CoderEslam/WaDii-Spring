package com.doubleclick.wadii.audit;

import com.doubleclick.wadii.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Append-only record of an important or administrative action. */
@Getter
@Entity
@Table(name = "audit_logs")
@NoArgsConstructor
public class AuditLog extends BaseEntity {

    @Column(name = "actor_id")
    private Long actorId;

    @Column(nullable = false)
    private String action;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    private String details;

    private String ip;

    public AuditLog(Long actorId, String action, String entityType, Long entityId, String details, String ip) {
        this.actorId = actorId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
        this.ip = ip;
    }
}
