package com.doubleclick.wadii.request;

import com.doubleclick.wadii.common.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * The customer's parent request: shared details (vehicle snapshot, location, timing) for one or more service
 * items. Its status only summarizes the items (see {@link RequestStatusService}); each item and each resulting
 * Service Order keeps its own state.
 */
@Getter
@Setter
@Entity
@Table(name = "service_requests")
public class ServiceRequest extends AuditedEntity {

    public enum Status { MATCHING, OFFERS_AVAILABLE, PARTIALLY_MATCHED, CLOSED, CANCELED, EXPIRED }

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "vehicle_id")
    private Long vehicleId;

    @Column(name = "vehicle_make")
    private String vehicleMake;

    @Column(name = "vehicle_model")
    private String vehicleModel;

    @Column(name = "vehicle_year")
    private Integer vehicleYear;

    @Column(name = "area_id", nullable = false)
    private Long areaId;

    @Column(nullable = false)
    private String address;

    private BigDecimal latitude;

    private BigDecimal longitude;

    @Column(name = "preferred_date")
    private LocalDate preferredDate;

    @Column(name = "preferred_time_from")
    private LocalTime preferredTimeFrom;

    @Column(name = "preferred_time_to")
    private LocalTime preferredTimeTo;

    private String notes;

    @Column(name = "photo_keys")
    private String photoKeys;

    @Column(name = "explicit_provider_id")
    private Long explicitProviderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.MATCHING;

    @Column(name = "response_deadline", nullable = false)
    private Instant responseDeadline;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Version
    private long version;

    public boolean hasVehicle() {
        return vehicleMake != null;
    }

    public String vehicleLabel() {
        return hasVehicle() ? vehicleMake + " " + vehicleModel + " " + vehicleYear : null;
    }

    public boolean isOpen() {
        return status == Status.MATCHING || status == Status.OFFERS_AVAILABLE || status == Status.PARTIALLY_MATCHED;
    }
}
