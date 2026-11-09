package com.doubleclick.wadii.provider;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Locale;

/**
 * A make (optionally model and year range) a provider services. A provider with no rows supports every vehicle.
 * Matching is a case-insensitive comparison; it is not a parts-fitment catalog.
 */
@Getter
@Entity
@Table(name = "provider_vehicle_support")
@NoArgsConstructor
public class VehicleSupport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(nullable = false)
    private String make;

    private String model;

    @Column(name = "year_from")
    private Integer yearFrom;

    @Column(name = "year_to")
    private Integer yearTo;

    public VehicleSupport(Long providerId, String make, String model, Integer yearFrom, Integer yearTo) {
        this.providerId = providerId;
        this.make = make;
        this.model = model;
        this.yearFrom = yearFrom;
        this.yearTo = yearTo;
    }

    public boolean covers(String vehicleMake, String vehicleModel, int vehicleYear) {
        if (!normalize(make).equals(normalize(vehicleMake))) return false;
        if (model != null && !normalize(model).equals(normalize(vehicleModel))) return false;
        if (yearFrom != null && vehicleYear < yearFrom) return false;
        return yearTo == null || vehicleYear <= yearTo;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
