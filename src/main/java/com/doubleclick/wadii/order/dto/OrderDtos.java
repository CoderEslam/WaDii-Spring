package com.doubleclick.wadii.order.dto;

import com.doubleclick.wadii.order.ActorRole;
import com.doubleclick.wadii.order.Order;
import com.doubleclick.wadii.order.OrderAttachment;
import com.doubleclick.wadii.order.OrderCancellation;
import com.doubleclick.wadii.order.OrderStatus;
import com.doubleclick.wadii.order.OrderTimelineEntry;
import com.doubleclick.wadii.order.TargetStatus;
import com.doubleclick.wadii.order.TimelineEvent;
import com.doubleclick.wadii.provider.dto.ProviderDtos.ServiceRef;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    /** The customer comes from the JWT; there is no userId field. */
    public record CreateOrderRequest(
            Long carTypeId,
            @NotBlank @Size(max = 200) String carDescription,
            @NotNull LocalDate requestedDate,
            @Size(max = 500) String address,
            @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            @Size(max = 4000) String comment,
            @NotEmpty @Size(max = 20) List<@NotNull Long> serviceIds,
            @Size(max = 30) List<@NotBlank @Size(max = 150) String> spareParts,
            Long providerId) {
    }

    /** Allowed only before any offer exists. */
    public record UpdateOrderRequest(@Size(max = 200) String carDescription, LocalDate requestedDate,
                                     @Size(max = 500) String address, @Size(max = 4000) String comment) {
    }

    public record CancelRequest(Long reasonId, @Size(max = 1000) String reason) {
    }

    public record SparePartView(Long id, String name) {
    }

    public record CounterpartView(Long userId, String firstName, String lastName, String image, String phone) {
    }

    public record OrderView(Long id, OrderStatus status, Long carTypeId, String carTypeName, String carDescription,
                            LocalDate requestedDate, String address, BigDecimal latitude, BigDecimal longitude, String comment,
                            List<ServiceRef> services, List<SparePartView> spareParts, Long selectedProviderId,
                            Long acceptedProviderId, Long acceptedResponseId, String currency, Instant responseDeadline,
                            Instant createdAt, Instant acceptedAt, Instant startedAt, Instant providerCompletedAt,
                            Instant completedAt, Instant canceledAt, Instant expiredAt,
                            CounterpartView customer, String relation, TargetStatus myTargetStatus, BigDecimal myDistanceKm) {
    }

    public static OrderView view(Order o, String relation, CounterpartView customer, TargetStatus targetStatus, BigDecimal distanceKm) {
        return new OrderView(o.getId(), o.getStatus(), o.getCarType() == null ? null : o.getCarType().getId(),
                o.getCarType() == null ? null : o.getCarType().getName(), o.getCarDescription(), o.getRequestedDate(),
                o.getAddress(), o.getLatitude(), o.getLongitude(), o.getComment(),
                o.getServices().stream().sorted(Comparator.comparing(s -> s.getName())).map(s -> new ServiceRef(s.getId(), s.getName())).toList(),
                o.getSpareParts().stream().map(p -> new SparePartView(p.getId(), p.getName())).toList(),
                o.getSelectedProvider() == null ? null : o.getSelectedProvider().getId(),
                o.getAcceptedProvider() == null ? null : o.getAcceptedProvider().getId(), o.getAcceptedResponseId(),
                o.getCurrency(), o.getResponseDeadline(), o.getCreatedAt(), o.getAcceptedAt(), o.getStartedAt(),
                o.getProviderCompletedAt(), o.getCompletedAt(), o.getCanceledAt(), o.getExpiredAt(),
                customer, relation, targetStatus, distanceKm);
    }

    public record TimelineView(TimelineEvent event, ActorRole actorRole, Long actorUserId, String details, Instant at) {
        public static TimelineView of(OrderTimelineEntry e) {
            return new TimelineView(e.getEvent(), e.getActorRole(), e.getActorUserId(), e.getDetails(), e.getCreatedAt());
        }
    }

    public record AttachmentView(Long id, String contentType, long sizeBytes, Long uploadedBy, Instant createdAt) {
        public static AttachmentView of(OrderAttachment a) {
            return new AttachmentView(a.getId(), a.getContentType(), a.getSizeBytes(), a.getUploadedBy(), a.getCreatedAt());
        }
    }

    public record CancellationView(ActorRole actorRole, Long canceledBy, Long reasonId, String reasonText,
                                   OrderStatus orderStatusAtCancel, Instant at) {
        public static CancellationView of(OrderCancellation c) {
            return new CancellationView(c.getActorRole(), c.getCanceledBy(), c.getReason() == null ? null : c.getReason().getId(),
                    c.getReasonText() != null ? c.getReasonText() : c.getReason() == null ? null : c.getReason().getText(),
                    c.getOrderStatusAtCancel(), c.getCreatedAt());
        }
    }

    public record CancelResult(OrderView order, List<Long> refundIds) {
    }
}
