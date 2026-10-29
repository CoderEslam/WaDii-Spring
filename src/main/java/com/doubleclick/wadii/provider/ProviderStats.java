package com.doubleclick.wadii.provider;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** Reliability metrics of a provider, recomputed by {@code ReliabilityService}. Rates are fractions between 0 and 1. */
@Entity
@Table(name = "provider_stats")
@Getter
@Setter
@NoArgsConstructor
public class ProviderStats {

    @Id
    @Column(name = "provider_id")
    private Long providerId;

    private int targetedCount;
    private int respondedCount;
    private int acceptedCount;
    private int completedCount;
    @Column(name = "provider_cancel_count")
    private int providerCancelCount;
    private int noShowCount;
    private int disputeCount;
    private BigDecimal avgResponseMinutes;
    private BigDecimal responseRate = BigDecimal.ZERO;
    private BigDecimal acceptanceRate = BigDecimal.ZERO;
    private BigDecimal completionRate = BigDecimal.ZERO;
    private BigDecimal cancellationRate = BigDecimal.ZERO;
    private BigDecimal noShowRate = BigDecimal.ZERO;
    private BigDecimal disputeRate = BigDecimal.ZERO;
    /** 0 to 100; 50 for providers without history. */
    private BigDecimal reliabilityScore = BigDecimal.valueOf(50);
    @Column(nullable = false)
    private Instant updatedAt;

    public ProviderStats(Long providerId) {
        this.providerId = providerId;
        this.updatedAt = Instant.now();
    }
}
