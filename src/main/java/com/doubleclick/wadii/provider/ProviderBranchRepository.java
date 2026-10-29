package com.doubleclick.wadii.provider;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProviderBranchRepository extends JpaRepository<ProviderBranch, Long> {

    List<ProviderBranch> findByProviderIdOrderById(Long providerId);
}
