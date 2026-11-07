package com.doubleclick.wadii.provider;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProviderDocumentRepository extends JpaRepository<ProviderDocument, Long> {

    List<ProviderDocument> findByProviderIdOrderByIdAsc(Long providerId);

    Optional<ProviderDocument> findByIdAndProviderId(Long id, Long providerId);

    long countByProviderId(Long providerId);
}
