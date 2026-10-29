package com.doubleclick.wadii.warranty;

import com.doubleclick.wadii.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Issued at completion from the WARRANTY policy snapshot when it grants a period. */
@Entity
@Table(name = "service_warranties")
@Getter
@Setter
@NoArgsConstructor
public class ServiceWarranty extends BaseEntity {

    @Column(name = "order_id", nullable = false, unique = true, updatable = false)
    private Long orderId;

    @Column(name = "provider_id", nullable = false, updatable = false)
    private Long providerId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false, updatable = false)
    private LocalDate endDate;

    @Column(nullable = false, updatable = false)
    private String terms;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WarrantyStatus status = WarrantyStatus.ACTIVE;

    public boolean isValidOn(LocalDate day) {
        return status == WarrantyStatus.ACTIVE && !day.isBefore(startDate) && !day.isAfter(endDate);
    }
}
