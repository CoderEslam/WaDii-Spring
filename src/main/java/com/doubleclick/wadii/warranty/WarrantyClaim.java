package com.doubleclick.wadii.warranty;

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

/** A customer's claim under an active warranty; the provider answers, and a disagreement escalates to a dispute. */
@Getter
@Setter
@Entity
@Table(name = "warranty_claims")
public class WarrantyClaim extends AuditedEntity {

    public enum Status { OPEN, ACCEPTED, REJECTED, ESCALATED, RESOLVED }

    @Column(name = "warranty_id", nullable = false, updatable = false)
    private Long warrantyId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "provider_id", nullable = false, updatable = false)
    private Long providerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    @Column(nullable = false)
    private String description;

    @Column(name = "evidence_keys")
    private String evidenceKeys;

    @Column(name = "provider_note")
    private String providerNote;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "dispute_id")
    private Long disputeId;

    @Version
    private long version;
}
