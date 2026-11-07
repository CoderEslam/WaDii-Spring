package com.doubleclick.wadii.provider;

import com.doubleclick.wadii.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One immutable version of a provider's customer-facing terms; accepted orders snapshot the version in force. */
@Getter
@Setter
@Entity
@Table(name = "provider_policies")
@NoArgsConstructor
public class ProviderPolicy extends BaseEntity {

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "policy_version", nullable = false)
    private int policyVersion;

    @Column(name = "cancellation_terms", nullable = false)
    private String cancellationTerms;

    @Column(name = "no_show_terms", nullable = false)
    private String noShowTerms;

    @Column(name = "service_conditions", nullable = false)
    private String serviceConditions;

    @Column(name = "return_terms", nullable = false)
    private String returnTerms;
}
