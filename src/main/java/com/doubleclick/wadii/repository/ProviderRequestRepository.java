package com.doubleclick.wadii.repository;

import com.doubleclick.wadii.entities.ProviderRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderRequestRepository extends JpaRepository<ProviderRequest, Long> {
    boolean existsByUserId(Long userId);
}