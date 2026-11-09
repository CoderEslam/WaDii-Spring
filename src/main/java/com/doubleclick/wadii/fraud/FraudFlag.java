package com.doubleclick.wadii.fraud;

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

/** An unusual pattern for staff to review. A flag never blocks anyone by itself. */
@Getter
@Setter
@Entity
@Table(name = "fraud_flags")
@NoArgsConstructor
public class FraudFlag extends BaseEntity {

    public enum Type {
        PROVIDER_CANCELLATIONS, CUSTOMER_CANCELLATIONS, EXCESSIVE_DISPUTES, CASH_CONFLICT, DUPLICATE_LISTING, MANUAL
    }

    public enum Status { OPEN, DISMISSED, ACTIONED }

    @Enumerated(EnumType.STRING)
    @Column(name = "flag_type", nullable = false)
    private Type type;

    @Column(name = "subject_user_id", nullable = false)
    private Long subjectUserId;

    @Column(nullable = false)
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "review_note")
    private String reviewNote;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;
}
