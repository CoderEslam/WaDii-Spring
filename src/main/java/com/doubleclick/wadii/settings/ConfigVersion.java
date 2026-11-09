package com.doubleclick.wadii.settings;

import com.doubleclick.wadii.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One immutable version of a configuration group. Changes add a row; nothing is updated in place. */
@Getter
@Setter
@Entity
@Table(name = "config_versions")
@NoArgsConstructor
public class ConfigVersion extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "config_key", nullable = false)
    private ConfigKey configKey;

    @Column(nullable = false)
    private String payload;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(nullable = false)
    private String reason;
}
