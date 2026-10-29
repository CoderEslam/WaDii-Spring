package com.doubleclick.wadii.provider;

import com.doubleclick.wadii.audit.AuditAction;
import com.doubleclick.wadii.audit.AuditService;
import com.doubleclick.wadii.catalog.CatalogService;
import com.doubleclick.wadii.catalog.CatalogServiceRepository;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.identity.Role;
import com.doubleclick.wadii.identity.User;
import com.doubleclick.wadii.identity.UserRepository;
import com.doubleclick.wadii.notification.NotificationService;
import com.doubleclick.wadii.notification.NotificationType;
import com.doubleclick.wadii.order.ActorRole;
import com.doubleclick.wadii.payment.PaymentMethod;
import com.doubleclick.wadii.provider.dto.ProviderDtos.*;
import com.doubleclick.wadii.storage.FileCategory;
import com.doubleclick.wadii.storage.FileStorageService;
import com.doubleclick.wadii.storage.StoredFile;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProviderService {

    private final ProviderRepository providerRepository;
    private final ProviderBranchRepository branchRepository;
    private final ProviderDocumentRepository documentRepository;
    private final ProviderPaymentAccountRepository paymentAccountRepository;
    private final ProviderStatsRepository statsRepository;
    private final CatalogServiceRepository catalogServiceRepository;
    private final UserRepository userRepository;
    private final ProviderAccess providerAccess;
    private final FileStorageService storage;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    // ------------------------------------------------------------------ public

    @Transactional(readOnly = true)
    public PageResponse<ProviderPublicView> search(Long serviceId, String q, Integer page, Integer size) {
        Specification<Provider> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("status"), ProviderStatus.ACTIVE));
            p.add(cb.equal(root.get("verificationStatus"), VerificationStatus.VERIFIED));
            if (serviceId != null) {
                Join<Provider, CatalogService> s = root.join("services");
                p.add(cb.equal(s.get("id"), serviceId));
                query.distinct(true);
            }
            if (q != null && !q.isBlank()) {
                p.add(cb.like(cb.lower(root.get("name")), "%" + q.trim().toLowerCase() + "%"));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        var result = providerRepository.findAll(spec, PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "ratingAvg").and(Sort.by("id"))));
        return PageResponse.of(result, ProviderPublicView::of);
    }

    @Transactional(readOnly = true)
    public ProviderPublicView publicProfile(Long id) {
        Provider p = providerAccess.require(id);
        if (p.getStatus() == ProviderStatus.BANNED) {
            throw BusinessException.notFound(ErrorCode.PROVIDER_NOT_FOUND);
        }
        return ProviderPublicView.of(p);
    }

    // ------------------------------------------------------------------ become a provider

    /** A USER applies to become a provider: role becomes PROVIDER, profile starts PENDING verification. */
    @Transactional
    public ProviderOwnView apply(AuthUser me, ApplyRequest request) {
        if (me.role() != Role.USER) {
            throw BusinessException.of(ErrorCode.PROVIDER_ALREADY_EXISTS, "Only customer accounts can apply to become providers");
        }
        if (providerRepository.existsByUserId(me.id())) {
            throw BusinessException.of(ErrorCode.PROVIDER_ALREADY_EXISTS);
        }
        User user = userRepository.findById(me.id()).orElseThrow();
        user.setRole(Role.PROVIDER);
        user.revokeTokens(); // the role is in the token; force a fresh login
        Provider p = new Provider();
        p.setUser(user);
        p.setName(request.name().trim());
        p.setDescription(request.description());
        p = providerRepository.save(p);
        statsRepository.save(new ProviderStats(p.getId()));
        auditService.record(me, ActorRole.CUSTOMER, AuditAction.ROLE_CHANGE, "PROVIDER", p.getId(), "USER -> PROVIDER (pending verification)");
        return ProviderOwnView.of(p);
    }

    // ------------------------------------------------------------------ own profile

    @Transactional(readOnly = true)
    public ProviderOwnView me(AuthUser me) {
        return ProviderOwnView.of(providerAccess.requireOwn(me));
    }

    @Transactional
    public ProviderOwnView update(AuthUser me, UpdateProviderRequest request) {
        Provider p = providerAccess.requireOwn(me);
        if (request.name() != null) {
            if (request.name().isBlank()) throw BusinessException.validation("name must not be blank");
            p.setName(request.name().trim());
        }
        if (request.description() != null) p.setDescription(request.description());
        if (request.serviceRadiusKm() != null) p.setServiceRadiusKm(request.serviceRadiusKm());
        return ProviderOwnView.of(p);
    }

    @Transactional
    public ProviderOwnView setServices(AuthUser me, List<Long> serviceIds) {
        Provider p = providerAccess.requireOwn(me);
        Set<Long> ids = new HashSet<>(serviceIds);
        List<CatalogService> services = catalogServiceRepository.findAllById(ids);
        if (services.size() != ids.size() || services.stream().anyMatch(s -> !s.isActive())) {
            throw BusinessException.validation("One or more service ids do not exist or are inactive");
        }
        p.getServices().clear();
        p.getServices().addAll(services);
        return ProviderOwnView.of(p);
    }

    @Transactional
    public ProviderOwnView setAvailability(AuthUser me, boolean acceptingOrders) {
        Provider p = providerAccess.requireOwn(me);
        p.setAcceptingOrders(acceptingOrders);
        return ProviderOwnView.of(p);
    }

    @Transactional
    public BranchView addBranch(AuthUser me, BranchRequest request) {
        Provider p = providerAccess.requireOwn(me);
        if (request.name() == null || request.name().isBlank()) {
            throw BusinessException.validation("name is required");
        }
        ProviderBranch b = new ProviderBranch();
        b.setProvider(p);
        applyBranch(b, request);
        p.getBranches().add(b);
        b = branchRepository.save(b);
        return BranchView.of(b);
    }

    @Transactional
    public BranchView updateBranch(AuthUser me, Long branchId, BranchRequest request) {
        ProviderBranch b = ownBranch(me, branchId);
        if (request.name() != null && request.name().isBlank()) {
            throw BusinessException.validation("name must not be blank");
        }
        applyBranch(b, request);
        return BranchView.of(b);
    }

    /** Branches are deactivated, not deleted: past orders and appointments reference them. */
    @Transactional
    public BranchView deactivateBranch(AuthUser me, Long branchId) {
        ProviderBranch b = ownBranch(me, branchId);
        b.setActive(false);
        return BranchView.of(b);
    }

    private ProviderBranch ownBranch(AuthUser me, Long branchId) {
        Provider p = providerAccess.requireOwn(me);
        ProviderBranch b = branchRepository.findById(branchId).orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND));
        if (!b.getProvider().getId().equals(p.getId())) {
            throw BusinessException.forbidden("This branch belongs to another provider");
        }
        return b;
    }

    private void applyBranch(ProviderBranch b, BranchRequest r) {
        if (r.name() != null) b.setName(r.name().trim());
        if (r.address() != null) b.setAddress(r.address().trim());
        if ((r.latitude() == null) != (r.longitude() == null)) {
            throw BusinessException.validation("latitude and longitude must be sent together");
        }
        if (r.latitude() != null) {
            b.setLatitude(r.latitude());
            b.setLongitude(r.longitude());
        }
        if (r.phone() != null) b.setPhone(r.phone().trim());
        if (r.active() != null) b.setActive(r.active());
        if (r.workingHours() != null) {
            Set<Integer> days = new HashSet<>();
            for (WorkingHoursRequest h : r.workingHours()) {
                if (!h.closesAt().isAfter(h.opensAt())) {
                    throw BusinessException.validation("closesAt must be after opensAt (day " + h.dayOfWeek() + ")");
                }
                if (!days.add(h.dayOfWeek())) {
                    throw BusinessException.validation("dayOfWeek " + h.dayOfWeek() + " is listed twice");
                }
            }
            b.getWorkingHours().clear();
            branchRepository.flush();
            for (WorkingHoursRequest h : r.workingHours()) {
                BranchWorkingHours wh = new BranchWorkingHours();
                wh.setBranch(b);
                wh.setDayOfWeek(h.dayOfWeek());
                wh.setOpensAt(h.opensAt());
                wh.setClosesAt(h.closesAt());
                b.getWorkingHours().add(wh);
            }
        }
    }

    @Transactional
    public ProviderOwnView setLinks(AuthUser me, List<String> urls) {
        Provider p = providerAccess.requireOwn(me);
        for (String url : urls) {
            if (!url.trim().matches("(?i)^https?://\\S+$")) {
                throw BusinessException.validation("Links must be http(s) URLs: " + url);
            }
        }
        p.getLinks().clear();
        providerRepository.flush();
        for (String url : urls) {
            ProviderLink link = new ProviderLink();
            link.setProvider(p);
            link.setUrl(url.trim());
            p.getLinks().add(link);
        }
        return ProviderOwnView.of(p);
    }

    // ------------------------------------------------------------------ payment receiving info

    @Transactional(readOnly = true)
    public List<PaymentAccountView> paymentAccounts(AuthUser me) {
        Provider p = providerAccess.requireOwn(me);
        return paymentAccountRepository.findByProviderIdOrderById(p.getId()).stream().map(PaymentAccountView::of).toList();
    }

    @Transactional
    public PaymentAccountView upsertPaymentAccount(AuthUser me, PaymentMethod method, PaymentAccountRequest request) {
        if (!method.usesProof()) {
            throw BusinessException.of(ErrorCode.PAYMENT_METHOD_NOT_SUPPORTED, "Receiving accounts exist only for VODAFONE_CASH and INSTAPAY");
        }
        Provider p = providerAccess.requireOwn(me);
        ProviderPaymentAccount account = paymentAccountRepository.findByProviderIdAndMethod(p.getId(), method)
                .orElseGet(() -> {
                    ProviderPaymentAccount a = new ProviderPaymentAccount();
                    a.setProvider(p);
                    a.setMethod(method);
                    return a;
                });
        account.setAccountName(request.accountName().trim());
        account.setAccountIdentifier(request.accountIdentifier().trim());
        account.setInstructions(request.instructions());
        account.setActive(true);
        return PaymentAccountView.of(paymentAccountRepository.save(account));
    }

    @Transactional
    public PaymentAccountView deactivatePaymentAccount(AuthUser me, PaymentMethod method) {
        Provider p = providerAccess.requireOwn(me);
        ProviderPaymentAccount account = paymentAccountRepository.findByProviderIdAndMethod(p.getId(), method)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND));
        account.setActive(false);
        return PaymentAccountView.of(account);
    }

    // ------------------------------------------------------------------ verification documents

    @Transactional
    public DocumentView uploadDocument(AuthUser me, DocumentType type, MultipartFile file) {
        Provider p = providerAccess.requireOwn(me);
        StoredFile stored = storage.store(file, FileCategory.PROVIDER_DOCUMENT);
        ProviderDocument d = new ProviderDocument();
        d.setProvider(p);
        d.setType(type);
        d.setFileKey(stored.key());
        d.setContentType(stored.contentType());
        d.setUploadedAt(clock.instant());
        return DocumentView.of(documentRepository.save(d));
    }

    @Transactional(readOnly = true)
    public List<DocumentView> documents(AuthUser me, Long providerId) {
        Provider p = providerId == null ? providerAccess.requireOwn(me) : providerAccess.require(providerId);
        return documentRepository.findByProviderIdOrderByUploadedAtDesc(p.getId()).stream().map(DocumentView::of).toList();
    }

    /** Owner or SUPPORT admin only. */
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> documentFile(AuthUser me, Long documentId) {
        ProviderDocument d = documentRepository.findById(documentId).orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND));
        boolean owner = d.getProvider().isOwnedBy(me.id());
        if (!owner && !me.isSupport()) {
            throw BusinessException.forbidden();
        }
        return storage.servePrivate(d.getFileKey(), d.getContentType());
    }

    // ------------------------------------------------------------------ admin

    @Transactional(readOnly = true)
    public PageResponse<ProviderOwnView> adminList(VerificationStatus verification, ProviderStatus status, Integer page, Integer size) {
        Specification<Provider> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (verification != null) p.add(cb.equal(root.get("verificationStatus"), verification));
            if (status != null) p.add(cb.equal(root.get("status"), status));
            return cb.and(p.toArray(new Predicate[0]));
        };
        return PageResponse.of(providerRepository.findAll(spec, PageResponse.pageable(page, size, Sort.by("id"))), ProviderOwnView::of);
    }

    @Transactional(readOnly = true)
    public ProviderOwnView adminGet(Long id) {
        return ProviderOwnView.of(providerAccess.require(id));
    }

    @Transactional
    public ProviderOwnView verify(AuthUser admin, Long id, VerificationDecision decision) {
        if (decision.decision() != VerificationStatus.VERIFIED && decision.decision() != VerificationStatus.REJECTED) {
            throw BusinessException.validation("decision must be VERIFIED or REJECTED");
        }
        Provider p = providerRepository.findByIdForUpdate(id).orElseThrow(() -> BusinessException.notFound(ErrorCode.PROVIDER_NOT_FOUND));
        p.setVerificationStatus(decision.decision());
        p.setStatusReason(decision.reason());
        boolean approved = decision.decision() == VerificationStatus.VERIFIED;
        if (approved) {
            p.setVerifiedAt(clock.instant());
            p.setVerifiedBy(admin.id());
        }
        auditService.record(admin, ActorRole.ADMIN, approved ? AuditAction.PROVIDER_APPROVED : AuditAction.PROVIDER_REJECTED,
                "PROVIDER", id, decision.reason());
        notificationService.notify(p.getUser().getId(),
                approved ? NotificationType.PROVIDER_APPROVED : NotificationType.PROVIDER_REJECTED,
                approved ? "Provider account verified" : "Provider verification rejected",
                approved ? "Your provider account is verified. You can now receive orders."
                        : "Your provider verification was rejected." + (decision.reason() == null ? "" : " Reason: " + decision.reason()),
                null);
        return ProviderOwnView.of(p);
    }

    /**
     * Suspends, bans or reactivates a provider. Suspended/banned providers stop receiving orders immediately:
     * their pending offers are withdrawn and open invitations declined. Orders they already won are NOT changed
     * automatically; the count is returned so support handles each one explicitly (reassign / cancel / refund).
     */
    @Transactional
    public StatusChangeResult changeStatus(AuthUser admin, Long id, StatusDecision decision) {
        Provider p = providerRepository.findByIdForUpdate(id).orElseThrow(() -> BusinessException.notFound(ErrorCode.PROVIDER_NOT_FOUND));
        ProviderStatus before = p.getStatus();
        p.setStatus(decision.status());
        p.setStatusReason(decision.reason());
        if (decision.status() != ProviderStatus.ACTIVE && p.getVerificationStatus() == VerificationStatus.VERIFIED) {
            p.setVerificationStatus(VerificationStatus.SUSPENDED);
        } else if (decision.status() == ProviderStatus.ACTIVE && p.getVerificationStatus() == VerificationStatus.SUSPENDED) {
            p.setVerificationStatus(VerificationStatus.VERIFIED);
        }
        ProviderStatusChanged event = new ProviderStatusChanged(p.getId(), decision.status());
        events.publishEvent(event);
        auditService.record(admin, ActorRole.ADMIN,
                decision.status() == ProviderStatus.ACTIVE ? AuditAction.PROVIDER_REACTIVATED : AuditAction.PROVIDER_SUSPENDED,
                "PROVIDER", id, before + " -> " + decision.status() + (decision.reason() == null ? "" : ": " + decision.reason()));
        if (decision.status() != ProviderStatus.ACTIVE) {
            notificationService.notify(p.getUser().getId(), NotificationType.PROVIDER_SUSPENDED, "Provider account " + decision.status().name().toLowerCase(),
                    "Your provider account was " + decision.status().name().toLowerCase() + "." + (decision.reason() == null ? "" : " Reason: " + decision.reason()), null);
        }
        return new StatusChangeResult(ProviderOwnView.of(p), event.withdrawnOffers, event.assignedOrders);
    }

    @Transactional(readOnly = true)
    public StatsView stats(AuthUser me) {
        Provider p = providerAccess.requireOwn(me);
        return StatsView.of(statsRepository.findById(p.getId()).orElseGet(() -> new ProviderStats(p.getId())));
    }

    /** Published synchronously inside the status-change transaction; listeners fill in the counters. */
    public static class ProviderStatusChanged {
        public final Long providerId;
        public final ProviderStatus status;
        public int withdrawnOffers;
        public long assignedOrders;

        public ProviderStatusChanged(Long providerId, ProviderStatus status) {
            this.providerId = providerId;
            this.status = status;
        }
    }
}
