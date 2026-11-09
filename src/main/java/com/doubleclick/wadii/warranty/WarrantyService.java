package com.doubleclick.wadii.warranty;

import com.doubleclick.wadii.audit.AuditService;
import com.doubleclick.wadii.catalog.CatalogService;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.common.util.KeyList;
import com.doubleclick.wadii.dispute.Dispute;
import com.doubleclick.wadii.dispute.DisputeRecorder;
import com.doubleclick.wadii.files.FileAccessRule;
import com.doubleclick.wadii.files.FilePurpose;
import com.doubleclick.wadii.files.FileService;
import com.doubleclick.wadii.notification.NotificationService;
import com.doubleclick.wadii.notification.NotificationType;
import com.doubleclick.wadii.offer.OfferLine;
import com.doubleclick.wadii.order.ServiceOrder;
import com.doubleclick.wadii.order.ServiceOrderRepository;
import com.doubleclick.wadii.provider.Provider;
import com.doubleclick.wadii.provider.ProviderRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

/** Warranty activation and claims. The provider is responsible for the warranty they explicitly offered. */
@Service
@RequiredArgsConstructor
public class WarrantyService implements FileAccessRule {

    public static final String FILE_REF = "WARRANTY_CLAIM";

    private final WarrantyRepository warranties;
    private final WarrantyClaimRepository claims;
    private final ServiceOrderRepository orders;
    private final ProviderRepository providers;
    private final CatalogService catalog;
    private final DisputeRecorder disputes;
    private final FileService files;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    public record WarrantyView(Long id, Long orderId, String category, String providerName, OfferLine.WarrantyCoverage coverage,
                               int durationDays, String exclusions, String conditions, Instant startsAt, Instant endsAt,
                               boolean active) {
    }

    public record ClaimView(Long id, Long warrantyId, Long orderId, WarrantyClaim.Status status, String description,
                            List<String> evidenceKeys, String providerNote, Long disputeId, Instant createdAt, Instant respondedAt) {
        static ClaimView of(WarrantyClaim c) {
            return new ClaimView(c.getId(), c.getWarrantyId(), c.getOrderId(), c.getStatus(), c.getDescription(),
                    KeyList.split(c.getEvidenceKeys()), c.getProviderNote(), c.getDisputeId(), c.getCreatedAt(), c.getRespondedAt());
        }
    }

    public record ClaimInput(@NotBlank @Size(max = 4000) String description, @Size(max = 10) List<String> evidenceKeys) {
    }

    public record RespondInput(boolean accept, @NotBlank @Size(max = 2000) String note) {
    }

    public record EscalateInput(@NotBlank @Size(max = 4000) String description) {
    }

    /** Called on customer-confirmed completion; starts the warranty from the confirmation time. */
    @Transactional
    public Warranty activate(ServiceOrder order) {
        if (!order.isWarrantyProvided() || warranties.findByOrderId(order.getId()).isPresent()) {
            return null;
        }
        Instant start = Instant.now(clock);
        Warranty w = new Warranty();
        w.setOrderId(order.getId());
        w.setCustomerId(order.getCustomerId());
        w.setProviderId(order.getProviderId());
        w.setCoverage(order.getWarrantyCoverage());
        w.setDurationDays(order.getWarrantyDays());
        w.setExclusions(order.getWarrantyExclusions());
        w.setConditions(order.getWarrantyConditions());
        w.setStartsAt(start);
        w.setEndsAt(start.plus(Duration.ofDays(order.getWarrantyDays())));
        return warranties.save(w);
    }

    @Transactional(readOnly = true)
    public List<WarrantyView> list(AuthUser user, boolean asProvider) {
        List<Warranty> list = asProvider
                ? providers.findByUserId(user.id()).map(p -> warranties.findByProviderIdOrderByIdDesc(p.getId())).orElse(List.of())
                : warranties.findByCustomerIdOrderByIdDesc(user.id());
        Instant now = Instant.now(clock);
        return list.stream().map(w -> {
            ServiceOrder o = orders.findById(w.getOrderId()).orElseThrow();
            String provider = providers.findById(w.getProviderId()).map(Provider::getBusinessName).orElse("");
            return new WarrantyView(w.getId(), w.getOrderId(), catalog.category(o.getCategoryId()).getCode(), provider,
                    w.getCoverage(), w.getDurationDays(), w.getExclusions(), w.getConditions(), w.getStartsAt(), w.getEndsAt(),
                    w.isActiveAt(now));
        }).toList();
    }

    @Transactional
    public ClaimView claim(AuthUser customer, Long warrantyId, ClaimInput input) {
        Warranty w = warranties.findById(warrantyId).filter(x -> x.getCustomerId().equals(customer.id()))
                .orElseThrow(() -> BusinessException.of(ErrorCode.WARRANTY_NOT_FOUND));
        if (!w.isActiveAt(Instant.now(clock))) {
            throw BusinessException.of(ErrorCode.WARRANTY_NOT_ACTIVE);
        }
        if (claims.existsByWarrantyIdAndStatusIn(warrantyId, EnumSet.of(WarrantyClaim.Status.OPEN, WarrantyClaim.Status.ACCEPTED))) {
            throw BusinessException.of(ErrorCode.CLAIM_INVALID_STATE, "A claim is already in progress for this warranty.");
        }
        WarrantyClaim c = new WarrantyClaim();
        c.setWarrantyId(warrantyId);
        c.setOrderId(w.getOrderId());
        c.setCustomerId(customer.id());
        c.setProviderId(w.getProviderId());
        c.setDescription(input.description().trim());
        c.setEvidenceKeys(KeyList.join(input.evidenceKeys()));
        claims.save(c);
        files.attach(customer.id(), input.evidenceKeys(), FilePurpose.EVIDENCE, FILE_REF, c.getId());
        notifications.notify(providerUser(w.getProviderId()), NotificationType.WARRANTY_CLAIM, "Warranty claim",
                "A customer filed a warranty claim for order " + w.getOrderId() + ".", "WARRANTY_CLAIM", c.getId());
        audit.record("WARRANTY_CLAIM_OPENED", "WARRANTY_CLAIM", c.getId(), null);
        return ClaimView.of(c);
    }

    @Transactional(readOnly = true)
    public PageResponse<ClaimView> claims(AuthUser user, boolean asProvider, Integer page, Integer size) {
        var pageable = PageResponse.pageable(page, size);
        if (asProvider) {
            Long providerId = providers.findByUserId(user.id()).map(Provider::getId)
                    .orElseThrow(() -> BusinessException.of(ErrorCode.PROVIDER_NOT_FOUND));
            return PageResponse.of(claims.findByProviderIdOrderByIdDesc(providerId, pageable), ClaimView::of);
        }
        return PageResponse.of(claims.findByCustomerIdOrderByIdDesc(user.id(), pageable), ClaimView::of);
    }

    @Transactional(readOnly = true)
    public PageResponse<ClaimView> adminClaims(WarrantyClaim.Status status, Integer page, Integer size) {
        return PageResponse.of(claims.search(status, PageResponse.pageable(page, size)), ClaimView::of);
    }

    @Transactional
    public ClaimView respond(AuthUser providerUser, Long claimId, RespondInput input) {
        WarrantyClaim c = claim(claimId);
        if (!providerUser(c.getProviderId()).equals(providerUser.id())) {
            throw BusinessException.of(ErrorCode.NOT_FOUND);
        }
        if (c.getStatus() != WarrantyClaim.Status.OPEN) {
            throw BusinessException.of(ErrorCode.CLAIM_INVALID_STATE);
        }
        c.setStatus(input.accept() ? WarrantyClaim.Status.ACCEPTED : WarrantyClaim.Status.REJECTED);
        c.setProviderNote(input.note().trim());
        c.setRespondedAt(Instant.now(clock));
        notifications.notify(c.getCustomerId(), NotificationType.WARRANTY_CLAIM_UPDATE,
                input.accept() ? "Warranty claim accepted" : "Warranty claim rejected", input.note(), "WARRANTY_CLAIM", claimId);
        audit.record("WARRANTY_CLAIM_" + c.getStatus(), "WARRANTY_CLAIM", claimId, input.note());
        return ClaimView.of(c);
    }

    /** The customer confirms the provider honored an accepted claim. */
    @Transactional
    public ClaimView resolve(AuthUser customer, Long claimId) {
        WarrantyClaim c = ownClaim(customer, claimId);
        if (c.getStatus() != WarrantyClaim.Status.ACCEPTED) {
            throw BusinessException.of(ErrorCode.CLAIM_INVALID_STATE);
        }
        c.setStatus(WarrantyClaim.Status.RESOLVED);
        notifications.notify(providerUser(c.getProviderId()), NotificationType.WARRANTY_CLAIM_UPDATE, "Warranty claim resolved",
                "The customer confirmed the claim was handled.", "WARRANTY_CLAIM", claimId);
        return ClaimView.of(c);
    }

    @Transactional
    public DisputeRecorder.View escalate(AuthUser customer, Long claimId, EscalateInput input) {
        WarrantyClaim c = ownClaim(customer, claimId);
        if (c.getStatus() != WarrantyClaim.Status.REJECTED && c.getStatus() != WarrantyClaim.Status.ACCEPTED) {
            throw BusinessException.of(ErrorCode.CLAIM_INVALID_STATE);
        }
        Dispute d = disputes.open(customer.id(), new DisputeRecorder.Subject(Dispute.Type.WARRANTY, c.getOrderId(), null, null,
                c.getId(), Dispute.PartyRole.CUSTOMER, providerUser(c.getProviderId())), input.description(), List.of());
        c.setStatus(WarrantyClaim.Status.ESCALATED);
        c.setDisputeId(d.getId());
        return disputes.view(d);
    }

    /** Applies a dispute decision on an escalated claim. */
    @Transactional
    public void closeEscalated(Long claimId, boolean honored) {
        WarrantyClaim c = claim(claimId);
        if (c.getStatus() == WarrantyClaim.Status.ESCALATED) {
            c.setStatus(honored ? WarrantyClaim.Status.ACCEPTED : WarrantyClaim.Status.RESOLVED);
        }
    }

    @Override
    public boolean supports(String refType) {
        return FILE_REF.equals(refType);
    }

    @Override
    public boolean canRead(AuthUser user, String refType, Long refId) {
        return claims.findById(refId).map(c -> c.getCustomerId().equals(user.id())
                || providerUser(c.getProviderId()).equals(user.id())).orElse(false);
    }

    private WarrantyClaim claim(Long id) {
        return claims.findById(id).orElseThrow(() -> BusinessException.of(ErrorCode.NOT_FOUND));
    }

    private WarrantyClaim ownClaim(AuthUser customer, Long id) {
        WarrantyClaim c = claim(id);
        if (!c.getCustomerId().equals(customer.id())) {
            throw BusinessException.of(ErrorCode.NOT_FOUND);
        }
        return c;
    }

    private Long providerUser(Long providerId) {
        return providers.findById(providerId).map(Provider::getUserId).orElseThrow();
    }
}
