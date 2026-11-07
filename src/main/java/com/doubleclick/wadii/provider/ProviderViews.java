package com.doubleclick.wadii.provider;

import com.doubleclick.wadii.catalog.CatalogService.CategoryView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

/** Request and response shapes of the provider endpoints (see docs/API_V1.md section 5). */
public final class ProviderViews {

    private static final String PHONE = "^0[0-9]{9,10}$";

    private ProviderViews() {
    }

    public record ApplicationInput(@NotBlank @Size(max = 120) String businessName, @Size(max = 2000) String description,
                                   @NotBlank @Pattern(regexp = PHONE, message = "must be an Egyptian phone number") String phone,
                                   @NotNull Long areaId, @NotBlank @Size(max = 300) String address,
                                   @NotNull @Size(max = 7) List<Long> categoryIds) {
    }

    public record DocumentInput(@NotNull ProviderDocument.Type type, @NotBlank String fileKey) {
    }

    public record DocumentView(Long id, ProviderDocument.Type type, String fileKey, String contentType, Instant uploadedAt) {
        static DocumentView of(ProviderDocument d) {
            return new DocumentView(d.getId(), d.getType(), d.getFileKey(), d.getContentType(), d.getCreatedAt());
        }
    }

    public record ApplicationView(Long providerId, String businessName, String description, String phone, Long areaId,
                                  String address, List<CategoryView> categories, List<DocumentView> documents,
                                  Provider.ApplicationStatus applicationStatus, Provider.Status providerStatus,
                                  String rejectionReason, String statusReason, Instant submittedAt, Instant reviewedAt,
                                  boolean acceptingOrders) {
    }

    public record ProfilePatch(@Size(max = 2000) String description,
                               @Pattern(regexp = PHONE, message = "must be an Egyptian phone number") String phone,
                               Boolean acceptingOrders) {
    }

    public record ServicesInput(@NotEmpty @Size(max = 7) List<Long> categoryIds) {
    }

    public record BranchInput(@NotBlank @Size(max = 120) String name, @NotNull Long areaId,
                              @NotBlank @Size(max = 300) String address,
                              @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
                              @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
                              @NotBlank @Pattern(regexp = PHONE, message = "must be an Egyptian phone number") String phone,
                              @Min(1) @Max(200) Integer serviceRadiusKm,
                              @Min(15) @Max(480) int slotMinutes, @Min(1) @Max(50) int maxBookingsPerSlot,
                              boolean active) {
    }

    public record HoursInput(@Min(1) @Max(7) int dayOfWeek, @NotNull LocalTime opensAt, @NotNull LocalTime closesAt) {
    }

    public record HoursRequest(@NotNull @Size(max = 7) List<@Valid HoursInput> hours) {
    }

    public record HoursView(int dayOfWeek, LocalTime opensAt, LocalTime closesAt) {
    }

    public record BranchView(Long id, String name, Long areaId, String address, BigDecimal latitude, BigDecimal longitude,
                             String phone, Integer serviceRadiusKm, int slotMinutes, int maxBookingsPerSlot,
                             boolean active, List<HoursView> hours) {
    }

    public record VehicleSupportInput(@NotBlank @Size(max = 60) String make, @Size(max = 60) String model,
                                      @Min(1950) @Max(2100) Integer yearFrom, @Min(1950) @Max(2100) Integer yearTo) {
    }

    public record VehicleSupportRequest(@NotNull @Size(max = 200) List<@Valid VehicleSupportInput> items) {
    }

    public record VehicleSupportView(Long id, String make, String model, Integer yearFrom, Integer yearTo) {
        static VehicleSupportView of(VehicleSupport v) {
            return new VehicleSupportView(v.getId(), v.getMake(), v.getModel(), v.getYearFrom(), v.getYearTo());
        }
    }

    public record PolicyInput(@NotBlank @Size(max = 4000) String cancellationTerms,
                              @NotBlank @Size(max = 4000) String noShowTerms,
                              @NotBlank @Size(max = 4000) String serviceConditions,
                              @NotBlank @Size(max = 4000) String returnTerms) {
    }

    public record PolicyView(int version, String cancellationTerms, String noShowTerms, String serviceConditions,
                             String returnTerms, Instant createdAt) {
        static PolicyView of(ProviderPolicy p) {
            return new PolicyView(p.getPolicyVersion(), p.getCancellationTerms(), p.getNoShowTerms(),
                    p.getServiceConditions(), p.getReturnTerms(), p.getCreatedAt());
        }
    }
}
