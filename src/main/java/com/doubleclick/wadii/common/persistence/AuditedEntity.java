package com.doubleclick.wadii.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PreUpdate;
import lombok.Getter;

import java.time.Instant;

/** Mutable rows: creation and last-update time. */
@Getter
@MappedSuperclass
public abstract class AuditedEntity extends BaseEntity {

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Override
    protected void onCreateHook() {
        updatedAt = getCreatedAt();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
