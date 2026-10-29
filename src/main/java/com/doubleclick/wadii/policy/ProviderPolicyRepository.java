package com.doubleclick.wadii.policy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProviderPolicyRepository extends JpaRepository<ProviderPolicy, Long> {

    List<ProviderPolicy> findByProviderId(Long providerId);

    Optional<ProviderPolicy> findByProviderIdAndPolicyType(Long providerId, PolicyType policyType);
}
