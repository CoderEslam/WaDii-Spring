package com.doubleclick.wadii.order;

import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.util.Times;
import com.doubleclick.wadii.provider.BranchHours;
import com.doubleclick.wadii.provider.BranchHoursRepository;
import com.doubleclick.wadii.provider.ProviderBranch;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Validates an appointment against the branch: in the future, inside the working hours of that weekday (a branch
 * without any configured hours is treated as always open), and under the branch capacity: at most
 * {@code maxBookingsPerSlot} active orders overlapping the same slot. The branch row is locked so two acceptances
 * cannot both take the last place.
 */
@Component
@RequiredArgsConstructor
public class AppointmentPolicy {

    static final Set<ServiceOrder.Status> OCCUPYING = EnumSet.of(ServiceOrder.Status.ACCEPTED, ServiceOrder.Status.IN_PROGRESS);

    private final BranchHoursRepository hours;
    private final ServiceOrderRepository orders;
    private final EntityManager entityManager;
    private final Clock clock;

    public record Slot(Instant start, Instant end) {
    }

    public Slot reserve(Long branchId, Instant start, Integer estimatedMinutes, Long excludeOrderId) {
        ProviderBranch branch = entityManager.find(ProviderBranch.class, branchId, LockModeType.PESSIMISTIC_WRITE);
        if (!start.isAfter(Instant.now(clock))) {
            throw BusinessException.of(ErrorCode.APPOINTMENT_IN_PAST);
        }
        int minutes = estimatedMinutes != null ? Math.max(estimatedMinutes, branch.getSlotMinutes()) : branch.getSlotMinutes();
        Instant end = start.plus(Duration.ofMinutes(minutes));
        List<BranchHours> week = hours.findByBranchId(branchId);
        if (!week.isEmpty()) {
            ZonedDateTime local = Times.local(start);
            int day = local.getDayOfWeek().getValue();
            LocalTime from = local.toLocalTime();
            LocalTime to = Times.local(end).toLocalTime();
            boolean sameDay = Times.local(end).toLocalDate().equals(local.toLocalDate());
            boolean open = week.stream().anyMatch(h -> h.getDayOfWeek() == day && !from.isBefore(h.getOpensAt())
                    && sameDay && !to.isAfter(h.getClosesAt()));
            if (!open) {
                throw BusinessException.of(ErrorCode.OUTSIDE_WORKING_HOURS);
            }
        }
        long overlapping = orders.countOverlapping(branchId, OCCUPYING, start, end, excludeOrderId);
        if (overlapping >= branch.getMaxBookingsPerSlot()) {
            throw BusinessException.of(ErrorCode.SLOT_FULL);
        }
        return new Slot(start, end);
    }
}
