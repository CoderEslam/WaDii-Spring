package com.doubleclick.wadii.vehicle;

import com.doubleclick.wadii.common.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A saved vehicle for reuse. It need not belong to the account holder, and requests copy make/model/year so
 * editing or archiving a vehicle never rewrites a past request.
 */
@Getter
@Setter
@Entity
@Table(name = "vehicles")
public class Vehicle extends AuditedEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false)
    private String make;

    @Column(nullable = false)
    private String model;

    @Column(name = "vehicle_year", nullable = false)
    private int year;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    public String label() {
        return make + " " + model + " " + year;
    }
}
