package com.doubleclick.wadii.provider;

import com.doubleclick.wadii.common.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Provider capability of a user account. Created as a DRAFT application; Provider Mode becomes usable once the
 * application is VERIFIED and the provider is ACTIVE. Suspending it never touches the owner's customer capability.
 */
@Getter
@Setter
@Entity
@Table(name = "providers")
public class Provider extends AuditedEntity {

    public enum ApplicationStatus { DRAFT, SUBMITTED, UNDER_REVIEW, VERIFIED, REJECTED }

    public enum Status { ACTIVE, SUSPENDED }

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    private String description;

    @Column(nullable = false)
    private String phone;

    @Column(name = "area_id", nullable = false)
    private Long areaId;

    @Column(nullable = false)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "application_status", nullable = false)
    private ApplicationStatus applicationStatus = ApplicationStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_status", nullable = false)
    private Status providerStatus = Status.ACTIVE;

    @Column(name = "accepting_orders", nullable = false)
    private boolean acceptingOrders = true;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "status_reason")
    private String statusReason;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Version
    private long version;

    public boolean isVerified() {
        return applicationStatus == ApplicationStatus.VERIFIED;
    }

    /** Provider Mode is open (the account status is checked separately by authentication). */
    public boolean canOperate() {
        return isVerified() && providerStatus == Status.ACTIVE;
    }

    public boolean isApplicationEditable() {
        return applicationStatus == ApplicationStatus.DRAFT || applicationStatus == ApplicationStatus.REJECTED;
    }
}
