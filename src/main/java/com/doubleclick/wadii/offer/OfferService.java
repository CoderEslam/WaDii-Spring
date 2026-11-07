package com.doubleclick.wadii.offer;

import com.doubleclick.wadii.audit.AuditService;
import com.doubleclick.wadii.catalog.CatalogService;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.notification.NotificationService;
import com.doubleclick.wadii.notification.NotificationType;
import com.doubleclick.wadii.offer.OfferViews.LineInput;
import com.doubleclick.wadii.offer.OfferViews.OfferInput;
import com.doubleclick.wadii.offer.OfferViews.PartView;
import com.doubleclick.wadii.offer.OfferViews.Pricing;
import com.doubleclick.wadii.offer.OfferViews.ProviderLineView;
import com.doubleclick.wadii.offer.OfferViews.ProviderOfferView;
import com.doubleclick.wadii.offer.OfferViews.Warranty;
import com.doubleclick.wadii.provider.Provider;
import com.doubleclick.wadii.provider.ProviderAccess;
import com.doubleclick.wadii.provider.ProviderBranchRepository;
import com.doubleclick.wadii.provider.ProviderCategory;
import com.doubleclick.wadii.provider.ProviderCategoryRepository;
import com.doubleclick.wadii.request.RequestStatusService;
import com.doubleclick.wadii.request.RequestTarget;
import com.doubleclick.wadii.request.RequestTargetRepository;
import com.doubleclick.wadii.request.ServiceItem;
import com.doubleclick.wadii.request.ServiceItemRepository;
import com.doubleclick.wadii.request.ServiceRequest;
import com.doubleclick.wadii.request.ServiceRequestRepository;
import com.doubleclick.wadii.settings.Configs.MarketplaceConfig;
import com.doubleclick.wadii.settings.PlatformConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Provider-side offer commands: submit, update while nothing is accepted, withdraw. */
@Service
@RequiredArgsConstructor
public class OfferService {

    private final OfferRepository offers;
    private final OfferLineRepository lines;
    private final OfferLinePartRepository parts;
    private final OfferStateService offerState;
    private final ServiceRequestRepository requests;
    private final ServiceItemRepository items;
    private final RequestTargetRepository targets;
    private final RequestStatusService requestStatus;
    private final ProviderAccess access;
    private final ProviderCategoryRepository providerCategories;
    private final ProviderBranchRepository branches;
    private final CatalogService catalog;
    private final PlatformConfigService config;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    @Transactional
    public ProviderOfferView submit(AuthUser user, Long requestId, OfferInput input) {
        Provider provider = access.forNewActivity(user);
        ServiceRequest request = openRequest(requestId);
        RequestTarget target = targetOf(requestId, provider.getId());
        Offer offer = offers.findByRequestIdAndProviderId(requestId, provider.getId()).orElse(null);
        if (offer != null && offer.getStatus() == Offer.Status.ACTIVE) {
            throw BusinessException.of(ErrorCode.OFFER_ALREADY_EXISTS);
        }
        if (offer != null && lines.findByOfferIdOrderByIdAsc(offer.getId()).stream().anyMatch(l -> l.getStatus() == OfferLine.Status.ACCEPTED)) {
            throw BusinessException.of(ErrorCode.OFFER_LOCKED);
        }
        if (offer == null) {
            offer = new Offer();
            offer.setRequestId(requestId);
            offer.setProviderId(provider.getId());
        } else {
            // A withdrawn or expired offer is reused: one offer row per provider and request keeps history readable.
            lines.deleteByOfferIdAndStatusIn(offer.getId(), EnumSet.of(OfferLine.Status.UNAVAILABLE));
            offer.setStatus(Offer.Status.ACTIVE);
        }
        apply(offer, provider, request, input);
        offers.save(offer);
        writeLines(offer, provider, request, input.lines());
        target.setStatus(RequestTarget.Status.OFFERED);
        if (target.getRespondedAt() == null) target.setRespondedAt(Instant.now(clock));
        requestStatus.refresh(requestId);
        notifications.notify(request.getCustomerId(), NotificationType.NEW_OFFER, "New offer",
                provider.getBusinessName() + " sent you an offer.", "REQUEST", requestId);
        audit.record("OFFER_SUBMITTED", "OFFER", offer.getId(), "request " + requestId);
        return view(offer);
    }

    @Transactional
    public ProviderOfferView update(AuthUser user, Long offerId, OfferInput input) {
        Provider provider = access.forNewActivity(user);
        Offer offer = ownLocked(provider, offerId);
        if (offer.getStatus() != Offer.Status.ACTIVE) {
            throw BusinessException.of(ErrorCode.OFFER_NOT_PENDING, "Only an active offer can be changed.");
        }
        List<OfferLine> existing = lines.findByOfferIdOrderByIdAsc(offerId);
        if (existing.stream().anyMatch(l -> l.getStatus() != OfferLine.Status.PENDING && l.getStatus() != OfferLine.Status.UNAVAILABLE)) {
            throw BusinessException.of(ErrorCode.OFFER_LOCKED);
        }
        ServiceRequest request = openRequest(offer.getRequestId());
        apply(offer, provider, request, input);
        parts.deleteByLineIdIn(existing.stream().map(OfferLine::getId).toList());
        lines.deleteByOfferIdAndStatusIn(offerId, EnumSet.of(OfferLine.Status.PENDING, OfferLine.Status.UNAVAILABLE));
        writeLines(offer, provider, request, input.lines());
        requestStatus.refresh(offer.getRequestId());
        notifications.notify(request.getCustomerId(), NotificationType.NEW_OFFER, "Offer updated",
                provider.getBusinessName() + " updated their offer.", "REQUEST", offer.getRequestId());
        audit.record("OFFER_UPDATED", "OFFER", offerId, null);
        return view(offer);
    }

    @Transactional
    public ProviderOfferView withdraw(AuthUser user, Long offerId) {
        Provider provider = access.operating(user);
        Offer offer = ownLocked(provider, offerId);
        if (offer.getStatus() != Offer.Status.ACTIVE) {
            throw BusinessException.of(ErrorCode.OFFER_NOT_PENDING);
        }
        withdrawLines(offer);
        audit.record("OFFER_WITHDRAWN", "OFFER", offerId, null);
        return view(offer);
    }

    /** Used when a provider is suspended: pending offers stop being acceptable. */
    @Transactional
    public void withdrawAllFor(Long providerId) {
        offers.findByProviderIdAndStatus(providerId, Offer.Status.ACTIVE).forEach(this::withdrawLines);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProviderOfferView> mine(AuthUser user, Offer.Status status, Integer page, Integer size) {
        Provider provider = access.operating(user);
        return PageResponse.of(offers.mine(provider.getId(), status, PageResponse.pageable(page, size)), this::view);
    }

    @Transactional(readOnly = true)
    public ProviderOfferView view(Offer offer) {
        List<OfferLine> list = lines.findByOfferIdOrderByIdAsc(offer.getId());
        Map<Long, List<PartView>> partsByLine = parts.findByLineIdIn(list.stream().map(OfferLine::getId).toList()).stream()
                .collect(Collectors.groupingBy(OfferLinePart::getLineId, Collectors.mapping(PartView::of, Collectors.toList())));
        Map<Long, ServiceItem> itemById = items.findAllById(list.stream().map(OfferLine::getServiceItemId).toList()).stream()
                .collect(Collectors.toMap(ServiceItem::getId, Function.identity()));
        Map<Long, String> codes = catalog.allCategories().stream()
                .collect(Collectors.toMap(CatalogService.CategoryView::id, CatalogService.CategoryView::code));
        List<ProviderLineView> lineViews = list.stream().map(l -> new ProviderLineView(l.getId(), l.getServiceItemId(),
                codes.get(itemById.get(l.getServiceItemId()).getCategoryId()), l.getStatus(), l.isAvailable(), Pricing.of(l),
                partsByLine.getOrDefault(l.getId(), List.of()), l.getEstimatedMinutes(), l.getProposedStart(), l.getNotes(),
                Warranty.of(l), l.getOrderId())).toList();
        return new ProviderOfferView(offer.getId(), offer.getRequestId(), offer.getStatus(), offer.getBranchId(), offer.getNotes(),
                offer.getExpiresAt(), offer.getCreatedAt(), lineViews);
    }

    private void withdrawLines(Offer offer) {
        lines.findByOfferIdOrderByIdAsc(offer.getId()).stream()
                .filter(l -> l.getStatus() == OfferLine.Status.PENDING || l.getStatus() == OfferLine.Status.UNAVAILABLE)
                .forEach(l -> l.setStatus(OfferLine.Status.WITHDRAWN));
        offer.setStatus(Offer.Status.WITHDRAWN);
        requestStatus.refresh(offer.getRequestId());
    }

    private void apply(Offer offer, Provider provider, ServiceRequest request, OfferInput input) {
        MarketplaceConfig cfg = config.marketplace();
        branches.findByIdAndProviderId(input.branchId(), provider.getId()).filter(b -> b.isActive())
                .orElseThrow(() -> BusinessException.validation("branchId must be one of your active branches."));
        int hours = input.validHours() == null ? cfg.offerValidityDefaultHours() : input.validHours();
        if (hours < cfg.offerValidityMinHours() || hours > cfg.offerValidityMaxHours()) {
            throw BusinessException.of(ErrorCode.INVALID_EXPIRY,
                    "Validity must be between " + cfg.offerValidityMinHours() + " and " + cfg.offerValidityMaxHours() + " hours.");
        }
        Instant expiresAt = Instant.now(clock).plus(Duration.ofHours(hours)).truncatedTo(ChronoUnit.SECONDS);
        // An offer never outlives the request's response deadline.
        offer.setExpiresAt(expiresAt.isAfter(request.getResponseDeadline()) ? request.getResponseDeadline() : expiresAt);
        offer.setBranchId(input.branchId());
        offer.setNotes(input.notes());
    }

    private void writeLines(Offer offer, Provider provider, ServiceRequest request, List<LineInput> input) {
        Map<Long, ServiceItem> requestItems = items.findByRequestIdOrderByIdAsc(request.getId()).stream()
                .collect(Collectors.toMap(ServiceItem::getId, Function.identity()));
        Set<Long> offered = providerCategories.findByProviderId(provider.getId()).stream()
                .map(ProviderCategory::getCategoryId).collect(Collectors.toSet());
        if (input.stream().map(LineInput::serviceItemId).distinct().count() != input.size()) {
            throw BusinessException.validation("Each service item can appear only once in an offer.");
        }
        if (input.stream().noneMatch(LineInput::available)) {
            throw BusinessException.validation("Price at least one service, or decline the request instead.");
        }
        var commission = config.commission();
        Instant now = Instant.now(clock);
        for (LineInput li : input) {
            ServiceItem item = requestItems.get(li.serviceItemId());
            if (item == null) {
                throw BusinessException.validation("serviceItemId " + li.serviceItemId() + " is not part of this request.");
            }
            if (!item.isOpen()) {
                throw BusinessException.of(ErrorCode.ITEM_NOT_OPEN);
            }
            if (!offered.contains(item.getCategoryId())) {
                throw BusinessException.of(ErrorCode.CATEGORY_NOT_OFFERED);
            }
            if (li.available()) {
                validateAvailableLine(li, now);
            }
            OfferLine line = new OfferLine();
            line.setOfferId(offer.getId());
            line.setServiceItemId(item.getId());
            line.setAvailable(li.available());
            line.setStatus(li.available() ? OfferLine.Status.PENDING : OfferLine.Status.UNAVAILABLE);
            OfferPricing.price(line, li, commission);
            line.setEstimatedMinutes(li.available() ? li.estimatedMinutes() : null);
            line.setProposedStart(li.available() ? li.proposedStart() : null);
            line.setNotes(li.notes());
            Warranty w = li.available() ? li.warranty() : new Warranty(false, null, null, null, null);
            line.setWarrantyProvided(w.provided());
            line.setWarrantyDays(w.provided() ? w.durationDays() : null);
            line.setWarrantyCoverage(w.provided() ? w.coverage() : null);
            line.setWarrantyExclusions(w.provided() ? w.exclusions() : null);
            line.setWarrantyConditions(w.conditions());
            lines.save(line);
            if (li.available() && li.parts() != null) {
                li.parts().forEach(p -> parts.save(new OfferLinePart(line.getId(), p.name().trim(), p.quantity(),
                        p.unitPrice(), OfferPricing.partTotal(p))));
            }
        }
    }

    private static void validateAvailableLine(LineInput li, Instant now) {
        if (li.servicePrice() == null && li.laborCost() == null && (li.parts() == null || li.parts().isEmpty())) {
            throw BusinessException.validation("An available service needs a price.");
        }
        Warranty w = li.warranty();
        if (w == null || w.provided() == null) {
            throw BusinessException.validation("State the warranty for every available service, or choose no warranty.");
        }
        if (w.provided() && (w.durationDays() == null || w.coverage() == null)) {
            throw BusinessException.validation("A warranty needs durationDays and coverage.");
        }
        if (li.proposedStart() != null && !li.proposedStart().isAfter(now)) {
            throw BusinessException.of(ErrorCode.APPOINTMENT_IN_PAST);
        }
    }

    private ServiceRequest openRequest(Long requestId) {
        ServiceRequest request = requests.findById(requestId).orElseThrow(() -> BusinessException.of(ErrorCode.REQUEST_NOT_FOUND));
        if (!request.isOpen()) {
            throw BusinessException.of(ErrorCode.ITEM_NOT_OPEN, "This request is no longer open.");
        }
        if (!request.getResponseDeadline().isAfter(Instant.now(clock))) {
            throw BusinessException.of(ErrorCode.REQUEST_DEADLINE_PASSED);
        }
        return request;
    }

    private RequestTarget targetOf(Long requestId, Long providerId) {
        return targets.findByRequestIdAndProviderId(requestId, providerId).orElseThrow(() -> BusinessException.of(ErrorCode.NOT_TARGETED));
    }

    private Offer ownLocked(Provider provider, Long offerId) {
        Offer offer = offers.lockById(offerId).orElseThrow(() -> BusinessException.of(ErrorCode.OFFER_NOT_FOUND));
        if (!offer.getProviderId().equals(provider.getId())) {
            throw BusinessException.of(ErrorCode.OFFER_NOT_FOUND);
        }
        return offer;
    }
}
