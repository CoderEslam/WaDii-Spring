package com.doubleclick.wadii.warranty;

import com.doubleclick.wadii.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A product (spare part) going back to the provider. Money is handled separately by a Refund, if any. */
@Entity
@Table(name = "return_requests")
@Getter
@Setter
@NoArgsConstructor
public class ReturnRequest extends BaseEntity {

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private Long requestedBy;

    @Column(nullable = false, updatable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReturnStatus status = ReturnStatus.REQUESTED;

    @Column(name = "refund_id")
    private Long refundId;

    @Column(name = "decided_by")
    private Long decidedBy;

    private String decisionNote;
    private Instant decidedAt;
}
