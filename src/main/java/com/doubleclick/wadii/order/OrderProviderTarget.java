package com.doubleclick.wadii.order;

import com.doubleclick.wadii.common.persistence.AuditedEntity;
import com.doubleclick.wadii.provider.Provider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** A provider invited to quote on an order, with the funnel status used for analytics. */
@Entity
@Table(name = "order_provider_targets")
@Getter
@Setter
@NoArgsConstructor
public class OrderProviderTarget extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false, updatable = false)
    private Provider provider;

    /** Nearest eligible branch at matching time. */
    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "distance_km", precision = 9, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "rank_score", precision = 9, scale = 4)
    private BigDecimal rankScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TargetStatus status = TargetStatus.TARGETED;

    private Instant notifiedAt;
    private Instant viewedAt;
    private Instant respondedAt;
}
