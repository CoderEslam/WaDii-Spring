package com.doubleclick.wadii.catalog;

import com.doubleclick.wadii.common.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One of the service categories. Whether vehicle details are required is decided per category by admins. */
@Getter
@Setter
@Entity
@Table(name = "service_categories")
public class ServiceCategory extends AuditedEntity {

    @Column(nullable = false, updatable = false)
    private String code;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "name_ar", nullable = false)
    private String nameAr;

    @Column(name = "vehicle_required", nullable = false)
    private boolean vehicleRequired;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
