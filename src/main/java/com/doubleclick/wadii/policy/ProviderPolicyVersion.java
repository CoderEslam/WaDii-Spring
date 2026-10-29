package com.doubleclick.wadii.policy;

import com.doubleclick.wadii.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;

/**
 * Immutable version of a provider policy.
 * <ul>
 *   <li>{@code periodDays}: return window (RETURN), refund window (REFUND), warranty length (WARRANTY).</li>
 *   <li>{@code feePercent}: late-cancellation fee (CANCELLATION) or no-show fee (NO_SHOW).</li>
 *   <li>{@code freeCancellationHours}: free cancellation until this many hours before the appointment.</li>
 * </ul>
 */
@Entity
@Immutable
@Table(name = "provider_policy_versions")
@Getter
@Setter
@NoArgsConstructor
public class ProviderPolicyVersion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private ProviderPolicy policy;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(nullable = false)
    private String terms;

    @Column(name = "period_days")
    private Integer periodDays;

    @Column(name = "fee_percent", precision = 5, scale = 2)
    private BigDecimal feePercent;

    @Column(name = "free_cancellation_hours")
    private Integer freeCancellationHours;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;
}
