package com.doubleclick.wadii.request;

import com.doubleclick.wadii.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** A provider invited to a request by matching, with its rank and response progress. */
@Getter
@Setter
@Entity
@Table(name = "request_targets")
public class RequestTarget extends BaseEntity {

    public enum Status { INVITED, VIEWED, OFFERED, DECLINED }

    @Column(name = "request_id", nullable = false, updatable = false)
    private Long requestId;

    @Column(name = "provider_id", nullable = false, updatable = false)
    private Long providerId;

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "distance_km")
    private BigDecimal distanceKm;

    @Column(name = "rank_score", nullable = false)
    private BigDecimal rankScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.INVITED;

    @Column(name = "viewed_at")
    private Instant viewedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;
}
