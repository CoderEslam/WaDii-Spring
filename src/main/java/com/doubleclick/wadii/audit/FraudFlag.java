package com.doubleclick.wadii.audit;

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

/** Rule-based abuse signal for admin review. V1 never blocks automatically on a flag. */
@Entity
@Table(name = "fraud_flags")
@Getter
@Setter
@NoArgsConstructor
public class FraudFlag extends BaseEntity {

    @Column(name = "subject_user_id")
    private Long subjectUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FraudFlagType type;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    private String details;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FraudFlagStatus status = FraudFlagStatus.OPEN;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    private Instant reviewedAt;
}
