package com.doubleclick.wadii.provider;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProviderPolicyRepository extends JpaRepository<ProviderPolicy, Long> {

    Optional<ProviderPolicy> findFirstByProviderIdOrderByPolicyVersionDesc(Long providerId);
}
