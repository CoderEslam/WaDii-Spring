package com.doubleclick.wadii.warranty;

import com.doubleclick.wadii.common.persistence.BaseEntity;
import com.doubleclick.wadii.offer.OfferLine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Warranty activated when the customer confirms completion, copied from the accepted offer line. */
@Getter
@Setter
@Entity
@Table(name = "warranties")
@NoArgsConstructor
public class Warranty extends BaseEntity {

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "provider_id", nullable = false, updatable = false)
    private Long providerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private OfferLine.WarrantyCoverage coverage;

    @Column(name = "duration_days", nullable = false, updatable = false)
    private int durationDays;

    @Column(updatable = false)
    private String exclusions;

    @Column(updatable = false)
    private String conditions;

    @Column(name = "starts_at", nullable = false, updatable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false, updatable = false)
    private Instant endsAt;

    public boolean isActiveAt(Instant now) {
        return !now.isBefore(startsAt) && now.isBefore(endsAt);
    }
}
