package com.doubleclick.wadii.provider;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/** Opening hours of a branch for one ISO weekday (1 = Monday), in Africa/Cairo local time. */
@Getter
@Entity
@Table(name = "branch_hours")
@NoArgsConstructor
public class BranchHours {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @Column(name = "opens_at", nullable = false)
    private LocalTime opensAt;

    @Column(name = "closes_at", nullable = false)
    private LocalTime closesAt;

    public BranchHours(Long branchId, int dayOfWeek, LocalTime opensAt, LocalTime closesAt) {
        this.branchId = branchId;
        this.dayOfWeek = dayOfWeek;
        this.opensAt = opensAt;
        this.closesAt = closesAt;
    }
}
