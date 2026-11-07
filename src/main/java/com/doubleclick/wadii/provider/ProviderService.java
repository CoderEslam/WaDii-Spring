package com.doubleclick.wadii.provider;

import com.doubleclick.wadii.audit.AuditService;
import com.doubleclick.wadii.catalog.CatalogService;
import com.doubleclick.wadii.catalog.CatalogService.CategoryView;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.files.FileAccessRule;
import com.doubleclick.wadii.files.FilePurpose;
import com.doubleclick.wadii.files.FileService;
import com.doubleclick.wadii.files.StoredFile;
import com.doubleclick.wadii.provider.ProviderViews.ApplicationInput;
import com.doubleclick.wadii.provider.ProviderViews.ApplicationView;
import com.doubleclick.wadii.provider.ProviderViews.BranchInput;
import com.doubleclick.wadii.provider.ProviderViews.BranchView;
import com.doubleclick.wadii.provider.ProviderViews.DocumentInput;
import com.doubleclick.wadii.provider.ProviderViews.DocumentView;
import com.doubleclick.wadii.provider.ProviderViews.HoursInput;
import com.doubleclick.wadii.provider.ProviderViews.HoursView;
import com.doubleclick.wadii.provider.ProviderViews.PolicyInput;
import com.doubleclick.wadii.provider.ProviderViews.PolicyView;
import com.doubleclick.wadii.provider.ProviderViews.ProfilePatch;
import com.doubleclick.wadii.provider.ProviderViews.VehicleSupportInput;
import com.doubleclick.wadii.provider.ProviderViews.VehicleSupportView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** The provider application workflow and everything a provider manages about their own business. */
@Service
@RequiredArgsConstructor
public class ProviderService implements FileAccessRule {

    public static final String FILE_REF = "APPLICATION";


    private final ProviderRepository providers;
    private final ProviderCategoryRepository providerCategories;
    private final ProviderDocumentRepository documents;
    private final ProviderBranchRepository branches;
    private final BranchHoursRepository hours;
    private final VehicleSupportRepository vehicleSupport;
    private final ProviderPolicyRepository policies;
    private final ProviderAccess access;
    private final CatalogService catalog;
    private final FileService files;
    private final AuditService audit;
    private final Clock clock;

    // ------------------------------------------------------------------ application

    @Transactional(readOnly = true)
    public ApplicationView application(AuthUser user) {
        return applicationView(access.own(user));
    }

    @Transactional
    public ApplicationView saveApplication(AuthUser user, ApplicationInput input) {
        if (user.admin()) {
            throw BusinessException.forbidden("Staff accounts cannot apply as providers.");
        }
        Provider p = providers.findByUserId(user.id()).orElseGet(() -> {
            Provider fresh = new Provider();
            fresh.setUserId(user.id());
            return fresh;
        });
        if (p.getId() != null && !p.isApplicationEditable()) {
            throw BusinessException.of(ErrorCode.APPLICATION_NOT_EDITABLE);
        }
        catalog.requireArea(input.areaId());
        p.setBusinessName(input.businessName().trim());
        p.setDescription(input.description());
        p.setPhone(input.phone());
        p.setAreaId(input.areaId());
        p.setAddress(input.address().trim());
        providers.save(p);
        replaceCategories(p.getId(), input.categoryIds());
        return applicationView(p);
    }

    @Transactional
    public DocumentView addDocument(AuthUser user, DocumentInput input) {
        Provider p = access.own(user);
        if (!p.isApplicationEditable()) {
            throw BusinessException.of(ErrorCode.APPLICATION_NOT_EDITABLE);
        }
        StoredFile file = files.attach(user.id(), List.of(input.fileKey()), FilePurpose.PROVIDER_DOCUMENT, FILE_REF, p.getId()).get(0);
        ProviderDocument d = new ProviderDocument();
        d.setProviderId(p.getId());
        d.setType(input.type());
        d.setFileKey(file.getFileKey());
        d.setContentType(file.getContentType());
        documents.save(d);
        return DocumentView.of(d);
    }

    @Transactional
    public void deleteDocument(AuthUser user, Long documentId) {
        Provider p = access.own(user);
        if (!p.isApplicationEditable()) {
            throw BusinessException.of(ErrorCode.APPLICATION_NOT_EDITABLE);
        }
        documents.delete(documents.findByIdAndProviderId(documentId, p.getId())
                .orElseThrow(() -> BusinessException.of(ErrorCode.NOT_FOUND)));
    }

    @Transactional
    public ApplicationView submit(AuthUser user) {
        Provider p = access.own(user);
        if (!p.isApplicationEditable()) {
            throw BusinessException.of(ErrorCode.APPLICATION_INVALID_STATE);
        }
        if (providerCategories.findByProviderId(p.getId()).isEmpty() || documents.countByProviderId(p.getId()) == 0) {
            throw BusinessException.of(ErrorCode.APPLICATION_INCOMPLETE);
        }
        p.setApplicationStatus(Provider.ApplicationStatus.SUBMITTED);
        p.setSubmittedAt(Instant.now(clock));
        p.setRejectionReason(null);
        audit.record("PROVIDER_APPLICATION_SUBMITTED", "PROVIDER", p.getId(), null);
        return applicationView(p);
    }

    // ------------------------------------------------------------------ profile

    @Transactional
    public ApplicationView patchProfile(AuthUser user, ProfilePatch patch) {
        Provider p = access.operating(user);
        if (patch.description() != null) p.setDescription(patch.description());
        if (patch.phone() != null) p.setPhone(patch.phone());
        if (patch.acceptingOrders() != null) p.setAcceptingOrders(patch.acceptingOrders());
        return applicationView(p);
    }

    @Transactional
    public ApplicationView setServices(AuthUser user, List<Long> categoryIds) {
        Provider p = access.operating(user);
        replaceCategories(p.getId(), categoryIds);
        audit.record("PROVIDER_SERVICES_CHANGED", "PROVIDER", p.getId(), categoryIds.toString());
        return applicationView(p);
    }

    // ------------------------------------------------------------------ branches

    @Transactional(readOnly = true)
    public List<BranchView> branches(AuthUser user) {
        Provider p = access.operating(user);
        return branchViews(branches.findByProviderIdOrderByIdAsc(p.getId()));
    }

    @Transactional
    public BranchView createBranch(AuthUser user, BranchInput input) {
        Provider p = access.operating(user);
        ProviderBranch b = new ProviderBranch();
        b.setProviderId(p.getId());
        apply(b, input);
        branches.save(b);
        return branchViews(List.of(b)).get(0);
    }

    @Transactional
    public BranchView updateBranch(AuthUser user, Long branchId, BranchInput input) {
        ProviderBranch b = ownBranch(user, branchId);
        apply(b, input);
        return branchViews(List.of(b)).get(0);
    }

    @Transactional
    public BranchView setHours(AuthUser user, Long branchId, List<HoursInput> input) {
        ProviderBranch b = ownBranch(user, branchId);
        if (input.stream().map(HoursInput::dayOfWeek).collect(Collectors.toSet()).size() != input.size()) {
            throw BusinessException.validation("Each weekday can be listed only once.");
        }
        if (input.stream().anyMatch(h -> !h.closesAt().isAfter(h.opensAt()))) {
            throw BusinessException.validation("closesAt must be after opensAt.");
        }
        hours.deleteByBranchId(b.getId());
        input.forEach(h -> hours.save(new BranchHours(b.getId(), h.dayOfWeek(), h.opensAt(), h.closesAt())));
        return branchViews(List.of(b)).get(0);
    }

    // ------------------------------------------------------------------ vehicle support and policy

    @Transactional(readOnly = true)
    public List<VehicleSupportView> vehicleSupport(AuthUser user) {
        Provider p = access.operating(user);
        return vehicleSupport.findByProviderIdOrderByIdAsc(p.getId()).stream().map(VehicleSupportView::of).toList();
    }

    @Transactional
    public List<VehicleSupportView> setVehicleSupport(AuthUser user, List<VehicleSupportInput> items) {
        Provider p = access.operating(user);
        for (VehicleSupportInput i : items) {
            if (i.yearFrom() != null && i.yearTo() != null && i.yearTo() < i.yearFrom()) {
                throw BusinessException.validation("yearTo must be >= yearFrom.");
            }
        }
        vehicleSupport.deleteByProviderId(p.getId());
        items.forEach(i -> vehicleSupport.save(new VehicleSupport(p.getId(), i.make().trim(),
                i.model() == null || i.model().isBlank() ? null : i.model().trim(), i.yearFrom(), i.yearTo())));
        return vehicleSupport.findByProviderIdOrderByIdAsc(p.getId()).stream().map(VehicleSupportView::of).toList();
    }

    @Transactional(readOnly = true)
    public PolicyView policy(AuthUser user) {
        Provider p = access.operating(user);
        return policies.findFirstByProviderIdOrderByPolicyVersionDesc(p.getId()).map(PolicyView::of)
                .orElse(new PolicyView(0, ProviderDefaults.POLICY.cancellationTerms(), ProviderDefaults.POLICY.noShowTerms(),
                        ProviderDefaults.POLICY.serviceConditions(), ProviderDefaults.POLICY.returnTerms(), null));
    }

    /** Each change is a new version; orders already accepted keep the version they snapshotted. */
    @Transactional
    public PolicyView savePolicy(AuthUser user, PolicyInput input) {
        Provider p = access.operating(user);
        int next = policies.findFirstByProviderIdOrderByPolicyVersionDesc(p.getId()).map(x -> x.getPolicyVersion() + 1).orElse(1);
        ProviderPolicy policy = new ProviderPolicy();
        policy.setProviderId(p.getId());
        policy.setPolicyVersion(next);
        policy.setCancellationTerms(input.cancellationTerms().trim());
        policy.setNoShowTerms(input.noShowTerms().trim());
        policy.setServiceConditions(input.serviceConditions().trim());
        policy.setReturnTerms(input.returnTerms().trim());
        policies.save(policy);
        audit.record("PROVIDER_POLICY_VERSIONED", "PROVIDER", p.getId(), "version " + next);
        return PolicyView.of(policy);
    }

    // ------------------------------------------------------------------ shared views

    @Transactional(readOnly = true)
    public ApplicationView applicationView(Provider p) {
        List<Long> categoryIds = providerCategories.findByProviderId(p.getId()).stream().map(ProviderCategory::getCategoryId).toList();
        List<CategoryView> categories = catalog.allCategories().stream().filter(c -> categoryIds.contains(c.id())).toList();
        List<DocumentView> docs = documents.findByProviderIdOrderByIdAsc(p.getId()).stream().map(DocumentView::of).toList();
        return new ApplicationView(p.getId(), p.getBusinessName(), p.getDescription(), p.getPhone(), p.getAreaId(),
                p.getAddress(), categories, docs, p.getApplicationStatus(), p.getProviderStatus(), p.getRejectionReason(),
                p.getStatusReason(), p.getSubmittedAt(), p.getReviewedAt(), p.isAcceptingOrders());
    }

    public List<BranchView> branchViews(List<ProviderBranch> list) {
        Map<Long, List<HoursView>> byBranch = hours.findByBranchIdIn(list.stream().map(ProviderBranch::getId).toList()).stream()
                .sorted(Comparator.comparingInt(BranchHours::getDayOfWeek))
                .collect(Collectors.groupingBy(BranchHours::getBranchId,
                        Collectors.mapping(h -> new HoursView(h.getDayOfWeek(), h.getOpensAt(), h.getClosesAt()), Collectors.toList())));
        return list.stream().map(b -> new BranchView(b.getId(), b.getName(), b.getAreaId(), b.getAddress(), b.getLatitude(),
                b.getLongitude(), b.getPhone(), b.getServiceRadiusKm(), b.getSlotMinutes(), b.getMaxBookingsPerSlot(),
                b.isActive(), byBranch.getOrDefault(b.getId(), List.of()))).toList();
    }

    @Override
    public boolean supports(String refType) {
        return FILE_REF.equals(refType);
    }

    @Override
    public boolean canRead(AuthUser user, String refType, Long refId) {
        return providers.findById(refId).map(p -> p.getUserId().equals(user.id())).orElse(false);
    }

    private void replaceCategories(Long providerId, List<Long> categoryIds) {
        List<Long> distinct = List.copyOf(new HashSet<>(categoryIds));
        if (!distinct.isEmpty()) {
            catalog.activeCategories(distinct);
        }
        providerCategories.deleteByProviderId(providerId);
        distinct.forEach(id -> providerCategories.save(new ProviderCategory(providerId, id)));
    }

    private ProviderBranch ownBranch(AuthUser user, Long branchId) {
        Provider p = access.operating(user);
        return branches.findByIdAndProviderId(branchId, p.getId()).orElseThrow(() -> BusinessException.of(ErrorCode.NOT_FOUND));
    }

    private void apply(ProviderBranch b, BranchInput input) {
        if ((input.latitude() == null) != (input.longitude() == null)) {
            throw BusinessException.validation("latitude and longitude must be sent together.");
        }
        catalog.requireArea(input.areaId());
        b.setName(input.name().trim());
        b.setAreaId(input.areaId());
        b.setAddress(input.address().trim());
        b.setLatitude(input.latitude());
        b.setLongitude(input.longitude());
        b.setPhone(input.phone());
        b.setServiceRadiusKm(input.serviceRadiusKm());
        b.setSlotMinutes(input.slotMinutes());
        b.setMaxBookingsPerSlot(input.maxBookingsPerSlot());
        b.setActive(input.active());
    }
}
