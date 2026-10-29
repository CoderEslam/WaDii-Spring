package com.doubleclick.wadii.policy;

import com.doubleclick.wadii.common.persistence.BaseEntity;
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

/** One policy type of a provider. Its content lives in append-only {@link ProviderPolicyVersion}s. */
@Entity
@Table(name = "provider_policies")
@Getter
@Setter
@NoArgsConstructor
public class ProviderPolicy extends BaseEntity {

    @Column(name = "provider_id", nullable = false, updatable = false)
    private Long providerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_type", nullable = false, updatable = false)
    private PolicyType policyType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_version_id")
    private ProviderPolicyVersion currentVersion;
}
