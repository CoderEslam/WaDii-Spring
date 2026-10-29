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

@Entity
@Table(name = "warranty_claims")
@Getter
@Setter
@NoArgsConstructor
public class WarrantyClaim extends BaseEntity {

    @Column(name = "warranty_id", nullable = false, updatable = false)
    private Long warrantyId;

    @Column(nullable = false, updatable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimStatus status = ClaimStatus.SUBMITTED;

    @Column(name = "provider_response")
    private String providerResponse;

    @Column(name = "dispute_id")
    private Long disputeId;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    private Instant decidedAt;
}
