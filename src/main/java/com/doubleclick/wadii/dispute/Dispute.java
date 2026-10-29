package com.doubleclick.wadii.dispute;

import com.doubleclick.wadii.common.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "disputes")
@Getter
@Setter
@NoArgsConstructor
public class Dispute extends AuditedEntity {

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "payment_id", updatable = false)
    private Long paymentId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "provider_id", nullable = false, updatable = false)
    private Long providerId;

    @Column(name = "opened_by", nullable = false, updatable = false)
    private Long openedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private DisputeType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisputeStatus status = DisputeStatus.OPEN;

    @Column(nullable = false, updatable = false)
    private String reason;

    private String description;
    private String resolution;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_outcome")
    private DisputeOutcome resolutionOutcome;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    private Instant resolvedAt;

    @Version
    private long version;
}
