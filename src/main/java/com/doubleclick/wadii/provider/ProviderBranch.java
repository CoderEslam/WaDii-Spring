package com.doubleclick.wadii.provider;

import com.doubleclick.wadii.common.persistence.AuditedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "provider_branches")
@Getter
@Setter
@NoArgsConstructor
public class ProviderBranch extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Column(nullable = false)
    private String name;

    private String address;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    private String phone;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "branch", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayOfWeek")
    private List<BranchWorkingHours> workingHours = new ArrayList<>();

    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }

    public Optional<BranchWorkingHours> hoursOn(DayOfWeek day) {
        return workingHours.stream().filter(h -> h.getDayOfWeek() == day.getValue()).findFirst();
    }

    /** True when the branch publishes no hours (schedule unknown) or is open on that day. */
    public boolean opensOn(DayOfWeek day) {
        return workingHours.isEmpty() || hoursOn(day).isPresent();
    }

    /** Whether [start, end] fits inside the published hours of that day. No published hours means it cannot be checked, so it is allowed. */
    public boolean covers(DayOfWeek day, LocalTime start, LocalTime end) {
        if (workingHours.isEmpty()) return true;
        if (end.isBefore(start)) return false;
        return hoursOn(day)
                .map(h -> !start.isBefore(h.getOpensAt()) && !end.isAfter(h.getClosesAt()))
                .orElse(false);
    }
}
