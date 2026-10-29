package com.doubleclick.wadii.appointment;

import com.doubleclick.wadii.audit.AuditAction;
import com.doubleclick.wadii.audit.AuditService;
import com.doubleclick.wadii.common.config.WadiiProperties;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.notification.NotificationService;
import com.doubleclick.wadii.notification.NotificationType;
import com.doubleclick.wadii.order.ActorRole;
import com.doubleclick.wadii.order.Order;
import com.doubleclick.wadii.order.OrderAccess;
import com.doubleclick.wadii.order.OrderCancellationService;
import com.doubleclick.wadii.order.OrderCancellationService.RefundRule;
import com.doubleclick.wadii.order.OrderStatus;
import com.doubleclick.wadii.order.OrderTimelineService;
import com.doubleclick.wadii.order.TimelineEvent;
import com.doubleclick.wadii.policy.PolicyService;
import com.doubleclick.wadii.policy.PolicyType;
import com.doubleclick.wadii.provider.ProviderBranch;
import com.doubleclick.wadii.provider.ProviderBranchRepository;
import com.doubleclick.wadii.response.OrderResponse;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Appointments of assigned orders. Times are validated against the branch working hours (platform time zone);
 * rescheduling never overwrites history: every change is an {@link AppointmentChange} row.
 */
@Service
@RequiredArgsConstructor
public class AppointmentService {

    private static final int DEFAULT_DURATION_MINUTES = 60;

    private final AppointmentRepository appointmentRepository;
    private final AppointmentChangeRepository changeRepository;
    private final ProviderBranchRepository branchRepository;
    private final OrderAccess orderAccess;
    private final OrderCancellationService cancellationService;
    private final PolicyService policyService;
    private final NotificationService notifications;
    private final AuditService audit;
    private final OrderTimelineService timeline;
    private final WadiiProperties properties;
    private final Clock clock;

    public record ScheduleRequest(Long branchId, @NotNull Instant startAt, @Size(max = 1000) String reason) {
    }

    public record NoShowRequest(@Size(max = 1000) String reason) {
    }

    public record ChangeView(Instant oldStart, Instant newStart, AppointmentStatus oldStatus, AppointmentStatus newStatus,
                             Long changedBy, String reason, Instant at) {
        static ChangeView of(AppointmentChange c) {
            return new ChangeView(c.getOldStart(), c.getNewStart(), c.getOldStatus(), c.getNewStatus(), c.getChangedBy(), c.getReason(), c.getCreatedAt());
        }
    }

    public record AppointmentView(Long id, Long orderId, Long branchId, String branchName, Instant scheduledStart,
                                  Instant scheduledEnd, AppointmentStatus status, Long noShowReportedBy, Instant noShowReportedAt,
                                  List<ChangeView> history) {
    }

    /** Called by the accept flow when the offer proposed a time. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void createFromOffer(Order order, OrderResponse response) {
        if (response.getProposedAppointmentAt() == null) return;
        int duration = response.getEstimatedDurationMinutes() == null ? DEFAULT_DURATION_MINUTES : response.getEstimatedDurationMinutes();
        Appointment a = new Appointment();
        a.setOrderId(order.getId());
        a.setBranch(response.getBranch());
        a.setScheduledStart(response.getProposedAppointmentAt());
        a.setScheduledEnd(response.getProposedAppointmentAt().plus(Duration.ofMinutes(duration)));
        a = appointmentRepository.save(a);
        record(a, null, null, AppointmentStatus.SCHEDULED, null, "from accepted offer");
        timeline.system(order.getId(), TimelineEvent.APPOINTMENT_SCHEDULED, a.getScheduledStart().toString());
    }

    /** Validates a proposed time for a branch. Used by offers and by scheduling. */
    public void validateSlot(ProviderBranch branch, Instant start, int durationMinutes) {
        if (!start.isAfter(clock.instant())) {
            throw BusinessException.of(ErrorCode.APPOINTMENT_IN_PAST);
        }
        if (branch == null) return;
        if (!branch.isActive()) {
            throw BusinessException.validation("The branch is not active");
        }
        ZonedDateTime s = start.atZone(properties.zone());
        ZonedDateTime e = s.plusMinutes(durationMinutes);
        if (!s.toLocalDate().equals(e.toLocalDate()) || !branch.covers(s.getDayOfWeek(), s.toLocalTime(), e.toLocalTime())) {
            throw BusinessException.of(ErrorCode.APPOINTMENT_OUTSIDE_WORKING_HOURS);
        }
    }

    /** First scheduling, or rescheduling an existing appointment (history preserved). Customer or accepted provider. */
    @Transactional
    public AppointmentView schedule(AuthUser me, Long orderId, ScheduleRequest request) {
        Order order = orderAccess.loadForUpdate(orderId);
        OrderAccess.Relation relation = orderAccess.requireParty(me, order);
        if (order.getStatus() != OrderStatus.ACCEPTED) {
            throw BusinessException.of(ErrorCode.ORDER_INVALID_STATE, "Appointments can be set while the order is ACCEPTED");
        }
        Appointment existing = appointmentRepository.findByOrderId(orderId).orElse(null);
        ProviderBranch branch = resolveBranch(order, request.branchId(), existing);
        long duration = existing == null ? DEFAULT_DURATION_MINUTES
                : Duration.between(existing.getScheduledStart(), existing.getScheduledEnd()).toMinutes();
        validateSlot(branch, request.startAt(), (int) duration);
        ActorRole role = roleOf(relation);
        Appointment a;
        if (existing == null) {
            a = new Appointment();
            a.setOrderId(orderId);
            a.setBranch(branch);
            a.setScheduledStart(request.startAt());
            a.setScheduledEnd(request.startAt().plus(Duration.ofMinutes(duration)));
            a = appointmentRepository.save(a);
            record(a, null, null, AppointmentStatus.SCHEDULED, me.id(), request.reason());
            timeline.add(orderId, TimelineEvent.APPOINTMENT_SCHEDULED, me.id(), role, request.startAt().toString());
        } else {
            if (!existing.getStatus().isOpen()) throw BusinessException.of(ErrorCode.APPOINTMENT_INVALID_STATE);
            if (request.reason() == null || request.reason().isBlank()) {
                throw BusinessException.validation("reason is required to reschedule");
            }
            a = existing;
            Instant old = a.getScheduledStart();
            AppointmentStatus oldStatus = a.getStatus();
            a.setBranch(branch);
            a.setScheduledStart(request.startAt());
            a.setScheduledEnd(request.startAt().plus(Duration.ofMinutes(duration)));
            a.setStatus(AppointmentStatus.RESCHEDULED);
            record(a, old, oldStatus, AppointmentStatus.RESCHEDULED, me.id(), request.reason());
            timeline.add(orderId, TimelineEvent.APPOINTMENT_RESCHEDULED, me.id(), role, old + " -> " + request.startAt() + ": " + request.reason());
            audit.record(me, role, AuditAction.APPOINTMENT_CHANGED, "APPOINTMENT", a.getId(), old + " -> " + request.startAt());
        }
        Long other = relation == OrderAccess.Relation.CUSTOMER ? order.getAcceptedProvider().getUser().getId() : order.getCustomer().getId();
        notifications.notify(other, existing == null ? NotificationType.APPOINTMENT_SCHEDULED : NotificationType.APPOINTMENT_RESCHEDULED,
                existing == null ? "Appointment scheduled" : "Appointment rescheduled",
                "Order #" + orderId + " appointment: " + request.startAt(), orderId);
        return view(a);
    }

    /**
     * Reports a no-show after the appointment time plus the grace period. The customer reports PROVIDER_NO_SHOW,
     * the provider reports CUSTOMER_NO_SHOW. Consequences are configurable (wadii.no-show.*): cancel the order and
     * refund (full for a provider no-show; minus the NO_SHOW policy fee for a customer no-show).
     * The appointment and order are kept as history, never deleted.
     */
    @Transactional
    public AppointmentView reportNoShow(AuthUser me, Long orderId, NoShowRequest request) {
        Order order = orderAccess.loadForUpdate(orderId);
        OrderAccess.Relation relation = orderAccess.requireParty(me, order);
        if (relation == OrderAccess.Relation.ADMIN) throw BusinessException.forbidden("Only the customer or the provider can report a no-show");
        if (order.getStatus() != OrderStatus.ACCEPTED) throw BusinessException.of(ErrorCode.ORDER_INVALID_STATE);
        Appointment a = appointmentRepository.findByOrderId(orderId).orElseThrow(() -> BusinessException.notFound(ErrorCode.APPOINTMENT_NOT_FOUND));
        if (!a.getStatus().isOpen()) throw BusinessException.of(ErrorCode.APPOINTMENT_INVALID_STATE);
        if (clock.instant().isBefore(a.getScheduledStart().plus(Duration.ofMinutes(properties.getNoShow().getGraceMinutes())))) {
            throw BusinessException.of(ErrorCode.NO_SHOW_TOO_EARLY);
        }
        boolean providerNoShow = relation == OrderAccess.Relation.CUSTOMER;
        AppointmentStatus outcome = providerNoShow ? AppointmentStatus.PROVIDER_NO_SHOW : AppointmentStatus.CUSTOMER_NO_SHOW;
        AppointmentStatus old = a.getStatus();
        a.setStatus(outcome);
        a.setNoShowReportedBy(me.id());
        a.setNoShowReportedAt(clock.instant());
        record(a, a.getScheduledStart(), old, outcome, me.id(), request == null ? null : request.reason());
        ActorRole role = roleOf(relation);
        timeline.add(orderId, TimelineEvent.NO_SHOW_REPORTED, me.id(), role, outcome.name());
        audit.record(me, role, AuditAction.NO_SHOW_REPORTED, "APPOINTMENT", a.getId(), outcome.name());
        Long other = providerNoShow ? order.getAcceptedProvider().getUser().getId() : order.getCustomer().getId();
        notifications.notify(other, NotificationType.NO_SHOW_REPORTED, "No-show reported",
                outcome + " was reported on order #" + orderId + ". You can open a dispute if this is wrong.", orderId);
        if (properties.getNoShow().isCancelOrder()) {
            RefundRule rule;
            if (providerNoShow) {
                rule = properties.getNoShow().isRefundOnProviderNoShow() ? RefundRule.full() : RefundRule.none();
            } else {
                BigDecimal fee = policyService.snapshotTerms(orderId, PolicyType.NO_SHOW).feePercent();
                rule = RefundRule.minusFee(fee == null ? BigDecimal.ZERO : fee);
            }
            cancellationService.cancel(order, null, ActorRole.SYSTEM, null, "No-show: " + outcome, rule,
                    com.doubleclick.wadii.payment.RefundSource.NO_SHOW);
        }
        return view(a);
    }

    @Transactional(readOnly = true)
    public AppointmentView get(AuthUser me, Long orderId) {
        Order order = orderAccess.load(orderId);
        orderAccess.requireParty(me, order);
        return view(appointmentRepository.findByOrderId(orderId).orElseThrow(() -> BusinessException.notFound(ErrorCode.APPOINTMENT_NOT_FOUND)));
    }

    /** Marks the open appointment of a completed order as COMPLETED. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void completeFor(Long orderId) {
        appointmentRepository.findByOrderId(orderId).filter(a -> a.getStatus().isOpen()).ifPresent(a -> {
            AppointmentStatus old = a.getStatus();
            a.setStatus(AppointmentStatus.COMPLETED);
            record(a, a.getScheduledStart(), old, AppointmentStatus.COMPLETED, null, "order completed");
        });
    }

    private ProviderBranch resolveBranch(Order order, Long branchId, Appointment existing) {
        if (branchId == null) return existing == null ? null : existing.getBranch();
        ProviderBranch b = branchRepository.findById(branchId).orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND));
        if (!b.getProvider().getId().equals(order.getAcceptedProvider().getId())) {
            throw BusinessException.validation("The branch does not belong to the accepted provider");
        }
        return b;
    }

    void record(Appointment a, Instant oldStart, AppointmentStatus oldStatus, AppointmentStatus newStatus, Long by, String reason) {
        AppointmentChange c = new AppointmentChange();
        c.setAppointmentId(a.getId());
        c.setOldStart(oldStart);
        c.setNewStart(a.getScheduledStart());
        c.setOldStatus(oldStatus);
        c.setNewStatus(newStatus);
        c.setChangedBy(by);
        c.setReason(reason);
        changeRepository.save(c);
    }

    private AppointmentView view(Appointment a) {
        return new AppointmentView(a.getId(), a.getOrderId(), a.getBranch() == null ? null : a.getBranch().getId(),
                a.getBranch() == null ? null : a.getBranch().getName(), a.getScheduledStart(), a.getScheduledEnd(), a.getStatus(),
                a.getNoShowReportedBy(), a.getNoShowReportedAt(),
                changeRepository.findByAppointmentIdOrderByCreatedAtAsc(a.getId()).stream().map(ChangeView::of).toList());
    }

    private static ActorRole roleOf(OrderAccess.Relation relation) {
        return switch (relation) {
            case CUSTOMER -> ActorRole.CUSTOMER;
            case ACCEPTED_PROVIDER, TARGETED_PROVIDER -> ActorRole.PROVIDER;
            case ADMIN -> ActorRole.ADMIN;
        };
    }
}
