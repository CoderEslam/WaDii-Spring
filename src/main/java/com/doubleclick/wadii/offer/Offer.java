package com.doubleclick.wadii.offer;

import com.doubleclick.wadii.common.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A provider's answer to one request: one line per service item it supports. The customer accepts lines, not
 * offers, so different items of the same request can go to different providers.
 */
@Getter
@Setter
@Entity
@Table(name = "offers")
public class Offer extends AuditedEntity {

    public enum Status { ACTIVE, WITHDRAWN, EXPIRED, CLOSED }

    @Column(name = "request_id", nullable = false, updatable = false)
    private Long requestId;

    @Column(name = "provider_id", nullable = false, updatable = false)
    private Long providerId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    private String notes;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Version
    private long version;
}
