package com.doubleclick.wadii.provider;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProviderStatsRepository extends JpaRepository<ProviderStats, Long> {

    List<ProviderStats> findByProviderIdIn(Collection<Long> providerIds);
}
