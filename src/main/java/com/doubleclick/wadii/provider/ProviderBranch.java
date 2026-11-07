package com.doubleclick.wadii.provider;

import com.doubleclick.wadii.common.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A provider location. Working hours say when it is open; {@code slotMinutes} and {@code maxBookingsPerSlot} say
 * how many appointments it can take at the same time (basic V1 capacity).
 */
@Getter
@Setter
@Entity
@Table(name = "provider_branches")
public class ProviderBranch extends AuditedEntity {

    @Column(name = "provider_id", nullable = false, updatable = false)
    private Long providerId;

    @Column(nullable = false)
    private String name;

    @Column(name = "area_id", nullable = false)
    private Long areaId;

    @Column(nullable = false)
    private String address;

    private BigDecimal latitude;

    private BigDecimal longitude;

    @Column(nullable = false)
    private String phone;

    @Column(name = "service_radius_km")
    private Integer serviceRadiusKm;

    @Column(name = "slot_minutes", nullable = false)
    private int slotMinutes;

    @Column(name = "max_bookings_per_slot", nullable = false)
    private int maxBookingsPerSlot;

    @Column(nullable = false)
    private boolean active = true;
}
