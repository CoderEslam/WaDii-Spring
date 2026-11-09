package com.doubleclick.wadii.provider;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProviderCategoryRepository extends JpaRepository<ProviderCategory, ProviderCategory.Key> {

    List<ProviderCategory> findByProviderId(Long providerId);

    boolean existsByProviderIdAndCategoryId(Long providerId, Long categoryId);

    @Modifying
    @Query("delete from ProviderCategory pc where pc.providerId = :providerId")
    void deleteByProviderId(@Param("providerId") Long providerId);
}
