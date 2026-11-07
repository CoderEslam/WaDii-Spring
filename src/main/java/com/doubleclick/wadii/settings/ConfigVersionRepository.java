package com.doubleclick.wadii.settings;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ConfigVersionRepository extends JpaRepository<ConfigVersion, Long> {

    @Query("""
            select c from ConfigVersion c where c.configKey = :key and c.effectiveFrom <= :now
            order by c.effectiveFrom desc, c.id desc
            """)
    List<ConfigVersion> inForce(@Param("key") ConfigKey key, @Param("now") Instant now, Pageable pageable);

    List<ConfigVersion> findByConfigKeyOrderByEffectiveFromDescIdDesc(ConfigKey key);
}
