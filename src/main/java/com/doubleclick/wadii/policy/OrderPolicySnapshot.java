package com.doubleclick.wadii.policy;

import com.doubleclick.wadii.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;

/** Immutable copy of a policy taken when the offer was accepted. Disputes and refunds read this, never the live policy. */
@Entity
@Immutable
@Table(name = "order_policy_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class OrderPolicySnapshot extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_type", nullable = false)
    private PolicyType policyType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PolicySource source;

    @Column(name = "provider_policy_version_id")
    private Long providerPolicyVersionId;

    @Column(name = "version_no")
    private Integer versionNo;

    @Column(nullable = false)
    private String terms;

    @Column(name = "period_days")
    private Integer periodDays;

    @Column(name = "fee_percent", precision = 5, scale = 2)
    private BigDecimal feePercent;

    @Column(name = "free_cancellation_hours")
    private Integer freeCancellationHours;
}
