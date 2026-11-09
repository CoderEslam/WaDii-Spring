package com.doubleclick.wadii.offer;

import com.doubleclick.wadii.settings.Configs;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Shapes shared by offer, request and order endpoints (docs/API_V1.md sections 6-8). */
public final class OfferViews {

    private OfferViews() {
    }

    public record Pricing(BigDecimal servicePrice, BigDecimal laborCost, BigDecimal partsTotal, BigDecimal discount,
                          BigDecimal providerTotal, BigDecimal commissionAmount, Configs.CommissionPayer commissionPayer,
                          BigDecimal customerTotal) {
        public static Pricing of(OfferLine l) {
            return new Pricing(l.getServicePrice(), l.getLaborCost(), l.getPartsTotal(), l.getDiscount(), l.getProviderTotal(),
                    l.getCommissionAmount(), l.getCommissionPayer(), l.getCustomerTotal());
        }
    }

    public record PartView(String name, int quantity, BigDecimal unitPrice, BigDecimal total) {
        public static PartView of(OfferLinePart p) {
            return new PartView(p.getName(), p.getQuantity(), p.getUnitPrice(), p.getTotal());
        }
    }

    public record Warranty(@NotNull Boolean provided, @Min(1) @Max(3650) Integer durationDays,
                           OfferLine.WarrantyCoverage coverage, @Size(max = 2000) String exclusions,
                           @Size(max = 2000) String conditions) {
        public static Warranty of(OfferLine l) {
            return l.isWarrantyProvided()
                    ? new Warranty(true, l.getWarrantyDays(), l.getWarrantyCoverage(), l.getWarrantyExclusions(), l.getWarrantyConditions())
                    : new Warranty(false, null, null, null, null);
        }
    }

    // ------------------------------------------------------------------ provider input

    public record PartInput(@NotBlank @Size(max = 120) String name, @Min(1) @Max(1000) int quantity,
                            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice) {
    }

    public record LineInput(@NotNull Long serviceItemId, boolean available,
                            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal servicePrice,
                            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal laborCost,
                            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal discount,
                            @Size(max = 50) List<@Valid PartInput> parts,
                            @Min(5) @Max(10080) Integer estimatedMinutes, Instant proposedStart,
                            @Size(max = 2000) String notes, @Valid Warranty warranty) {
    }

    public record OfferInput(@NotNull Long branchId, @Min(1) @Max(336) Integer validHours, @Size(max = 2000) String notes,
                             @NotEmpty @Size(max = 7) List<@Valid LineInput> lines) {
    }

    // ------------------------------------------------------------------ provider view

    public record ProviderLineView(Long lineId, Long serviceItemId, String categoryCode, OfferLine.Status status,
                                   boolean available, Pricing pricing, List<PartView> parts, Integer estimatedMinutes,
                                   Instant proposedStart, String notes, Warranty warranty, Long orderId) {
    }

    public record ProviderOfferView(Long id, Long requestId, Offer.Status status, Long branchId, String notes,
                                    Instant expiresAt, Instant createdAt, List<ProviderLineView> lines) {
    }
}
