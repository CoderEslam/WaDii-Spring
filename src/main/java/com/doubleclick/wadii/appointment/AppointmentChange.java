package com.doubleclick.wadii.appointment;

import com.doubleclick.wadii.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/** Append-only history of reschedules and status changes. */
@Entity
@Immutable
@Table(name = "appointment_changes")
@Getter
@Setter
@NoArgsConstructor
public class AppointmentChange extends BaseEntity {

    @Column(name = "appointment_id", nullable = false)
    private Long appointmentId;

    @Column(name = "old_start")
    private Instant oldStart;

    @Column(name = "new_start")
    private Instant newStart;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status")
    private AppointmentStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false)
    private AppointmentStatus newStatus;

    @Column(name = "changed_by")
    private Long changedBy;

    private String reason;
}
