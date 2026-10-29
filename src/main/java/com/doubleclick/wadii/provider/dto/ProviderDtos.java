package com.doubleclick.wadii.provider.dto;

import com.doubleclick.wadii.payment.PaymentMethod;
import com.doubleclick.wadii.provider.BranchWorkingHours;
import com.doubleclick.wadii.provider.DocumentStatus;
import com.doubleclick.wadii.provider.DocumentType;
import com.doubleclick.wadii.provider.Provider;
import com.doubleclick.wadii.provider.ProviderBranch;
import com.doubleclick.wadii.provider.ProviderDocument;
import com.doubleclick.wadii.provider.ProviderLink;
import com.doubleclick.wadii.provider.ProviderOffer;
import com.doubleclick.wadii.provider.ProviderPaymentAccount;
import com.doubleclick.wadii.provider.ProviderStats;
import com.doubleclick.wadii.provider.ProviderStatus;
import com.doubleclick.wadii.provider.VerificationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

/** Request/response records of the provider module. */
public final class ProviderDtos {

    private ProviderDtos() {
    }

    public record ServiceRef(Long id, String name) {
    }

    public record WorkingHoursView(int dayOfWeek, LocalTime opensAt, LocalTime closesAt) {
        public static WorkingHoursView of(BranchWorkingHours h) {
            return new WorkingHoursView(h.getDayOfWeek(), h.getOpensAt(), h.getClosesAt());
        }
    }

    public record BranchView(Long id, String name, String address, BigDecimal latitude, BigDecimal longitude, String phone,
                             boolean active, List<WorkingHoursView> workingHours) {
        public static BranchView of(ProviderBranch b) {
            return new BranchView(b.getId(), b.getName(), b.getAddress(), b.getLatitude(), b.getLongitude(), b.getPhone(),
                    b.isActive(), b.getWorkingHours().stream().map(WorkingHoursView::of).toList());
        }
    }

    /** What any authenticated user can see. No internal ranking data. */
    public record ProviderPublicView(Long id, String name, String description, String image, String backgroundImage,
                                     boolean verified, BigDecimal ratingAvg, int ratingCount, int followersCount,
                                     boolean acceptingOrders, List<ServiceRef> services, List<BranchView> branches,
                                     List<String> links) {
        public static ProviderPublicView of(Provider p) {
            return new ProviderPublicView(p.getId(), p.getName(), p.getDescription(), p.getUser().getImage(),
                    p.getUser().getBackgroundImage(), p.getVerificationStatus() == VerificationStatus.VERIFIED,
                    p.getRatingAvg(), p.getRatingCount(), p.getFollowersCount(), p.isEligibleForOrders(),
                    serviceRefs(p), p.getBranches().stream().filter(ProviderBranch::isActive).map(BranchView::of).toList(),
                    p.getLinks().stream().map(ProviderLink::getUrl).toList());
        }
    }

    /** The owner's (or an admin's) view. */
    public record ProviderOwnView(Long id, Long userId, String name, String description, ProviderStatus status,
                                  VerificationStatus verificationStatus, String statusReason, boolean acceptingOrders,
                                  boolean eligibleForOrders, BigDecimal serviceRadiusKm, BigDecimal ratingAvg, int ratingCount,
                                  int followersCount, Instant verifiedAt, List<ServiceRef> services, List<BranchView> branches,
                                  List<String> links, Instant createdAt) {
        public static ProviderOwnView of(Provider p) {
            return new ProviderOwnView(p.getId(), p.getUser().getId(), p.getName(), p.getDescription(), p.getStatus(),
                    p.getVerificationStatus(), p.getStatusReason(), p.isAcceptingOrders(), p.isEligibleForOrders(),
                    p.getServiceRadiusKm(), p.getRatingAvg(), p.getRatingCount(), p.getFollowersCount(), p.getVerifiedAt(),
                    serviceRefs(p), p.getBranches().stream().map(BranchView::of).toList(),
                    p.getLinks().stream().map(ProviderLink::getUrl).toList(), p.getCreatedAt());
        }
    }

    private static List<ServiceRef> serviceRefs(Provider p) {
        return p.getServices().stream().sorted(Comparator.comparing(s -> s.getName()))
                .map(s -> new ServiceRef(s.getId(), s.getName())).toList();
    }

    public record ApplyRequest(@NotBlank @Size(max = 150) String name, @Size(max = 2000) String description) {
    }

    public record UpdateProviderRequest(@Size(max = 150) String name, @Size(max = 2000) String description,
                                        @DecimalMin("1") @DecimalMax("500") BigDecimal serviceRadiusKm) {
    }

    public record ServicesRequest(@NotNull List<Long> serviceIds) {
    }

    public record AvailabilityRequest(@NotNull Boolean acceptingOrders) {
    }

    public record BranchRequest(@Size(max = 150) String name, @Size(max = 500) String address,
                                @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
                                @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
                                @Size(max = 30) String phone, Boolean active,
                                @Valid List<WorkingHoursRequest> workingHours) {
    }

    public record WorkingHoursRequest(@Min(1) @Max(7) int dayOfWeek, @NotNull LocalTime opensAt, @NotNull LocalTime closesAt) {
    }

    public record LinksRequest(@NotNull @Size(max = 10) List<@NotBlank @Size(max = 500) String> links) {
    }

    public record PaymentAccountRequest(@NotBlank @Size(max = 150) String accountName,
                                        @NotBlank @Size(max = 150) String accountIdentifier,
                                        @Size(max = 500) String instructions) {
    }

    public record PaymentAccountView(Long id, PaymentMethod method, String accountName, String accountIdentifier,
                                     String instructions, boolean active) {
        public static PaymentAccountView of(ProviderPaymentAccount a) {
            return new PaymentAccountView(a.getId(), a.getMethod(), a.getAccountName(), a.getAccountIdentifier(),
                    a.getInstructions(), a.isActive());
        }
    }

    public record DocumentView(Long id, DocumentType type, String contentType, DocumentStatus status, Instant uploadedAt,
                               Instant reviewedAt) {
        public static DocumentView of(ProviderDocument d) {
            return new DocumentView(d.getId(), d.getType(), d.getContentType(), d.getStatus(), d.getUploadedAt(), d.getReviewedAt());
        }
    }

    public record VerificationDecision(@NotNull VerificationStatus decision, @Size(max = 500) String reason) {
    }

    public record StatusDecision(@NotNull ProviderStatus status, @Size(max = 500) String reason) {
    }

    public record StatusChangeResult(ProviderOwnView provider, int withdrawnOffers, long assignedOrdersNeedingAction) {
    }

    public record StatsView(int targetedCount, int respondedCount, int acceptedCount, int completedCount,
                            int providerCancelCount, int noShowCount, int disputeCount, BigDecimal avgResponseMinutes,
                            BigDecimal responseRate, BigDecimal acceptanceRate, BigDecimal completionRate,
                            BigDecimal cancellationRate, BigDecimal noShowRate, BigDecimal disputeRate,
                            BigDecimal reliabilityScore, Instant updatedAt) {
        public static StatsView of(ProviderStats s) {
            return new StatsView(s.getTargetedCount(), s.getRespondedCount(), s.getAcceptedCount(), s.getCompletedCount(),
                    s.getProviderCancelCount(), s.getNoShowCount(), s.getDisputeCount(), s.getAvgResponseMinutes(),
                    s.getResponseRate(), s.getAcceptanceRate(), s.getCompletionRate(), s.getCancellationRate(),
                    s.getNoShowRate(), s.getDisputeRate(), s.getReliabilityScore(), s.getUpdatedAt());
        }
    }

    public record OfferRequest(@Size(max = 200) String title, @Size(max = 4000) String description, LocalDate endDate,
                               List<Long> serviceIds) {
    }

    public record OfferView(Long id, Long providerId, String providerName, String title, String description,
                            LocalDate endDate, List<ServiceRef> services, Boolean saved) {
        public static OfferView of(ProviderOffer o, Boolean saved) {
            return new OfferView(o.getId(), o.getProvider().getId(), o.getProvider().getName(), o.getTitle(), o.getDescription(),
                    o.getEndDate(), o.getServices().stream().map(s -> new ServiceRef(s.getId(), s.getName())).toList(), saved);
        }
    }
}
