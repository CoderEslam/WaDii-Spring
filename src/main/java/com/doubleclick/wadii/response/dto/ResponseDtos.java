package com.doubleclick.wadii.response.dto;

import com.doubleclick.wadii.response.OrderResponse;
import com.doubleclick.wadii.response.ResponseStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ResponseDtos {

    private ResponseDtos() {
    }

    public record PartPriceRequest(@NotNull Long sparePartId,
                                   @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal price) {
    }

    /**
     * The provider comes from the JWT. price = laborCost + partsCost - discount, computed by the server.
     * If {@code partPrices} is sent, partsCost is their sum. {@code expiresAt} defaults to now + 48h and must be
     * between now + 1h and now + 7 days; it is capped at the order response deadline.
     */
    public record SubmitResponseRequest(
            @NotNull Long orderId,
            @NotBlank @Size(max = 4000) String description,
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal laborCost,
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal partsCost,
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal discount,
            @Valid @Size(max = 30) List<PartPriceRequest> partPrices,
            @Min(5) @Max(60 * 24 * 14) Integer estimatedDurationMinutes,
            Instant expiresAt,
            Long branchId,
            Instant proposedAppointmentAt) {
    }

    public record UpdateResponseRequest(
            @Size(max = 4000) String description,
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal laborCost,
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal partsCost,
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal discount,
            @Valid @Size(max = 30) List<PartPriceRequest> partPrices,
            @Min(5) @Max(60 * 24 * 14) Integer estimatedDurationMinutes,
            Instant expiresAt,
            Long branchId,
            Instant proposedAppointmentAt) {
    }

    public record PartPriceView(Long sparePartId, String name, BigDecimal price) {
    }

    public record ProviderSummary(Long id, String name, String image, BigDecimal ratingAvg, int ratingCount, boolean verified) {
    }

    public record BranchSummary(Long id, String name, String address, BigDecimal distanceKm) {
    }

    public record ResponseView(Long id, Long orderId, ProviderSummary provider, BranchSummary branch, BigDecimal laborCost,
                               BigDecimal partsCost, BigDecimal discount, BigDecimal price, String currency, String description,
                               Integer estimatedDurationMinutes, Instant proposedAppointmentAt, Instant expiresAt,
                               ResponseStatus status, Instant statusChangedAt, List<PartPriceView> partPrices,
                               Instant createdAt, Instant updatedAt) {
        public static ResponseView of(OrderResponse r, BigDecimal distanceKm) {
            var p = r.getProvider();
            return new ResponseView(r.getId(), r.getOrder().getId(),
                    new ProviderSummary(p.getId(), p.getName(), p.getUser().getImage(), p.getRatingAvg(), p.getRatingCount(),
                            p.getVerificationStatus() == com.doubleclick.wadii.provider.VerificationStatus.VERIFIED),
                    r.getBranch() == null ? null : new BranchSummary(r.getBranch().getId(), r.getBranch().getName(), r.getBranch().getAddress(), distanceKm),
                    r.getLaborCost(), r.getPartsCost(), r.getDiscount(), r.getPrice(), r.getCurrency(), r.getDescription(),
                    r.getEstimatedDurationMinutes(), r.getProposedAppointmentAt(), r.getExpiresAt(), r.getStatus(), r.getStatusChangedAt(),
                    r.getPartPrices().stream().map(pp -> new PartPriceView(pp.getSparePart().getId(), pp.getSparePart().getName(), pp.getPrice())).toList(),
                    r.getCreatedAt(), r.getUpdatedAt());
        }
    }

    public enum SortMode {
        RECOMMENDED,
        LOWEST_PRICE,
        HIGHEST_RATED,
        NEAREST,
        FASTEST_RESPONSE
    }

    public record AcceptResult(ResponseView accepted, Long orderId, Long paymentId, Long conversationId) {
    }
}
