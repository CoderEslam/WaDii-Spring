package com.doubleclick.wadii.vehicle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    /** Last-used first (the client preselects the first one), then newest. */
    @Query("""
            select v from Vehicle v where v.userId = :userId and v.archived = false
            order by case when v.lastUsedAt is null then 1 else 0 end, v.lastUsedAt desc, v.id desc
            """)
    List<Vehicle> listForUser(@Param("userId") Long userId);

    Optional<Vehicle> findByIdAndUserIdAndArchivedFalse(Long id, Long userId);
}
