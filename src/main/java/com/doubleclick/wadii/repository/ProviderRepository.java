package com.doubleclick.wadii.repository;

import com.doubleclick.wadii.entities.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProviderRepository extends JpaRepository<Provider, Long> {

    Optional<Provider> findByUserId(Long userId);

    @Query("SELECT DISTINCT p FROM Provider p JOIN p.services s WHERE s.id = :serviceId")
    List<Provider> findAllByServiceId(@Param("serviceId") Long serviceId);
}
