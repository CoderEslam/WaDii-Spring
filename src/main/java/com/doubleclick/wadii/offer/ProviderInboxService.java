package com.doubleclick.wadii.offer;

import com.doubleclick.wadii.catalog.CatalogService;
import com.doubleclick.wadii.catalog.CatalogService.CategoryView;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.common.util.KeyList;
import com.doubleclick.wadii.offer.OfferViews.ProviderOfferView;
import com.doubleclick.wadii.provider.Provider;
import com.doubleclick.wadii.provider.ProviderAccess;
import com.doubleclick.wadii.provider.ProviderCategory;
import com.doubleclick.wadii.provider.ProviderCategoryRepository;
import com.doubleclick.wadii.request.RequestQueryService.VehicleView;
import com.doubleclick.wadii.request.RequestTarget;
import com.doubleclick.wadii.request.RequestTargetRepository;
import com.doubleclick.wadii.request.ServiceItem;
import com.doubleclick.wadii.request.ServiceItemRepository;
import com.doubleclick.wadii.request.ServiceRequest;
import com.doubleclick.wadii.request.ServiceRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What a provider sees of requests sent to them: only the service items in categories they offer, and no
 * customer contact data before an offer is accepted.
 */
@Service
@RequiredArgsConstructor
public class ProviderInboxService {

    private final RequestTargetRepository targets;
    private final ServiceRequestRepository requests;
    private final ServiceItemRepository items;
    private final OfferRepository offers;
    private final OfferService offerService;
    private final ProviderAccess access;
    private final ProviderCategoryRepository providerCategories;
    private final CatalogService catalog;
    private final Clock clock;

    public record InboxRow(Long requestId, RequestTarget.Status targetStatus, ServiceRequest.Status requestStatus, Long areaId,
                           String vehicleLabel, List<String> matchingCategories, Instant responseDeadline,
                           BigDecimal distanceKm, Instant invitedAt, Long myOfferId) {
    }

    public record ItemRow(Long id, CategoryView category, String description, ServiceItem.Status status) {
    }

    public record RequestView(Long requestId, ServiceRequest.Status requestStatus, RequestTarget.Status targetStatus,
                              VehicleView vehicle, Long areaId, String address, BigDecimal latitude, BigDecimal longitude,
                              LocalDate preferredDate, LocalTime preferredTimeFrom, LocalTime preferredTimeTo, String notes,
                              List<String> photoKeys, Instant responseDeadline, List<ItemRow> items, ProviderOfferView myOffer) {
    }

    @Transactional(readOnly = true)
    public PageResponse<InboxRow> inbox(AuthUser user, RequestTarget.Status status, Integer page, Integer size) {
        Provider provider = access.operating(user);
        Set<Long> offered = offeredCategories(provider.getId());
        Map<Long, CategoryView> categories = categoryMap();
        var result = targets.inbox(provider.getId(), status, PageResponse.pageable(page, size));
        List<Long> requestIds = result.getContent().stream().map(RequestTarget::getRequestId).toList();
        Map<Long, ServiceRequest> requestById = requests.findAllById(requestIds).stream()
                .collect(Collectors.toMap(ServiceRequest::getId, Function.identity()));
        Map<Long, List<ServiceItem>> itemsByRequest = requestIds.isEmpty() ? Map.of()
                : items.findByRequestIdIn(requestIds).stream().collect(Collectors.groupingBy(ServiceItem::getRequestId));
        return PageResponse.of(result, t -> {
            ServiceRequest r = requestById.get(t.getRequestId());
            List<String> matching = itemsByRequest.getOrDefault(r.getId(), List.of()).stream()
                    .filter(i -> offered.contains(i.getCategoryId()))
                    .map(i -> categories.get(i.getCategoryId()).code()).toList();
            Long offerId = offers.findByRequestIdAndProviderId(r.getId(), provider.getId()).map(Offer::getId).orElse(null);
            return new InboxRow(r.getId(), t.getStatus(), r.getStatus(), r.getAreaId(), r.vehicleLabel(), matching,
                    r.getResponseDeadline(), t.getDistanceKm(), t.getCreatedAt(), offerId);
        });
    }

    @Transactional
    public RequestView view(AuthUser user, Long requestId) {
        Provider provider = access.operating(user);
        RequestTarget target = targets.findByRequestIdAndProviderId(requestId, provider.getId())
                .orElseThrow(() -> BusinessException.of(ErrorCode.NOT_TARGETED));
        if (target.getViewedAt() == null) {
            target.setViewedAt(Instant.now(clock));
            if (target.getStatus() == RequestTarget.Status.INVITED) target.setStatus(RequestTarget.Status.VIEWED);
        }
        ServiceRequest r = requests.findById(requestId).orElseThrow();
        Set<Long> offered = offeredCategories(provider.getId());
        Map<Long, CategoryView> categories = categoryMap();
        List<ItemRow> rows = items.findByRequestIdOrderByIdAsc(requestId).stream()
                .filter(i -> offered.contains(i.getCategoryId()))
                .map(i -> new ItemRow(i.getId(), categories.get(i.getCategoryId()), i.getDescription(), i.getStatus())).toList();
        ProviderOfferView myOffer = offers.findByRequestIdAndProviderId(requestId, provider.getId()).map(offerService::view).orElse(null);
        return new RequestView(r.getId(), r.getStatus(), target.getStatus(), VehicleView.of(r), r.getAreaId(), r.getAddress(),
                r.getLatitude(), r.getLongitude(), r.getPreferredDate(), r.getPreferredTimeFrom(), r.getPreferredTimeTo(),
                r.getNotes(), KeyList.split(r.getPhotoKeys()), r.getResponseDeadline(), rows, myOffer);
    }

    @Transactional
    public void decline(AuthUser user, Long requestId) {
        Provider provider = access.operating(user);
        RequestTarget target = targets.findByRequestIdAndProviderId(requestId, provider.getId())
                .orElseThrow(() -> BusinessException.of(ErrorCode.NOT_TARGETED));
        if (target.getStatus() == RequestTarget.Status.OFFERED) {
            throw BusinessException.of(ErrorCode.OFFER_ALREADY_EXISTS, "Withdraw your offer instead.");
        }
        target.setStatus(RequestTarget.Status.DECLINED);
        if (target.getRespondedAt() == null) target.setRespondedAt(Instant.now(clock));
    }

    private Set<Long> offeredCategories(Long providerId) {
        return providerCategories.findByProviderId(providerId).stream().map(ProviderCategory::getCategoryId).collect(Collectors.toSet());
    }

    private Map<Long, CategoryView> categoryMap() {
        return catalog.allCategories().stream().collect(Collectors.toMap(CategoryView::id, Function.identity()));
    }
}
