package com.doubleclick.wadii.provider;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProviderBranchRepository extends JpaRepository<ProviderBranch, Long> {

    List<ProviderBranch> findByProviderIdOrderByIdAsc(Long providerId);

    List<ProviderBranch> findByProviderIdInAndActiveTrue(Collection<Long> providerIds);

    Optional<ProviderBranch> findByIdAndProviderId(Long id, Long providerId);
}
