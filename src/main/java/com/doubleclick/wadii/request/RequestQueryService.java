package com.doubleclick.wadii.request;

import com.doubleclick.wadii.catalog.CatalogService;
import com.doubleclick.wadii.catalog.CatalogService.CategoryView;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.common.util.Geo;
import com.doubleclick.wadii.common.util.KeyList;
import com.doubleclick.wadii.offer.Offer;
import com.doubleclick.wadii.offer.OfferLine;
import com.doubleclick.wadii.offer.OfferLinePart;
import com.doubleclick.wadii.offer.OfferLinePartRepository;
import com.doubleclick.wadii.offer.OfferLineRepository;
import com.doubleclick.wadii.offer.OfferRepository;
import com.doubleclick.wadii.offer.OfferViews.PartView;
import com.doubleclick.wadii.offer.OfferViews.Pricing;
import com.doubleclick.wadii.offer.OfferViews.Warranty;
import com.doubleclick.wadii.order.ProviderStats;
import com.doubleclick.wadii.provider.Provider;
import com.doubleclick.wadii.provider.ProviderBranch;
import com.doubleclick.wadii.provider.ProviderBranchRepository;
import com.doubleclick.wadii.provider.ProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Customer-facing read models of requests (docs/API_V1.md section 6). */
@Service
@RequiredArgsConstructor
public class RequestQueryService {

    private final ServiceRequestRepository requests;
    private final ServiceItemRepository items;
    private final RequestTargetRepository targets;
    private final OfferRepository offers;
    private final OfferLineRepository lines;
    private final OfferLinePartRepository parts;
    private final ProviderRepository providers;
    private final ProviderBranchRepository branches;
    private final ProviderStats stats;
    private final CatalogService catalog;
    private final RequestService requestService;

    public record VehicleView(String make, String model, Integer year, String label) {
        public static VehicleView of(ServiceRequest r) {
            return r.hasVehicle() ? new VehicleView(r.getVehicleMake(), r.getVehicleModel(), r.getVehicleYear(), r.vehicleLabel()) : null;
        }
    }

    public record RequestSummary(Long id, ServiceRequest.Status status, String vehicleLabel, Long areaId, int itemCount,
                                 List<String> categories, long offerCount, long acceptedCount, Instant responseDeadline,
                                 Instant createdAt) {
    }

    public record ProviderCard(Long id, String businessName, BigDecimal ratingAverage, long ratingCount, long completedOrders) {
    }

    public record BranchCard(Long id, String name, String address, Long areaId, BigDecimal latitude, BigDecimal longitude,
                             BigDecimal distanceKm) {
    }

    public record OfferLineForCustomer(Long lineId, Long offerId, OfferLine.Status status, ProviderCard provider,
                                       BranchCard branch, Pricing pricing, List<PartView> parts, Integer estimatedMinutes,
                                       Instant proposedStart, String notes, Warranty warranty, Instant expiresAt,
                                       Instant submittedAt) {
    }

    public record ItemView(Long id, CategoryView category, String description, ServiceItem.Status status, Long orderId,
                           List<OfferLineForCustomer> offers) {
    }

    public record RequestDetail(Long id, ServiceRequest.Status status, VehicleView vehicle, Long areaId, String address,
                                BigDecimal latitude, BigDecimal longitude, LocalDate preferredDate,
                                LocalTime preferredTimeFrom, LocalTime preferredTimeTo, String notes, List<String> photoKeys,
                                Instant responseDeadline, boolean editable, long invitedProviders, Instant createdAt,
                                List<ItemView> items) {
    }

    @Transactional(readOnly = true)
    public PageResponse<RequestSummary> mine(AuthUser customer, ServiceRequest.Status status, Integer page, Integer size) {
        var result = requests.mine(customer.id(), status, PageResponse.pageable(page, size));
        return summaries(result);
    }

    @Transactional(readOnly = true)
    public PageResponse<RequestSummary> adminList(ServiceRequest.Status status, Integer page, Integer size) {
        return summaries(requests.adminSearch(status, PageResponse.pageable(page, size)));
    }

    @Transactional(readOnly = true)
    public RequestDetail detail(AuthUser user, Long id) {
        return detail(requestService.visible(user, id));
    }

    @Transactional(readOnly = true)
    public RequestDetail detail(ServiceRequest r) {
        Map<Long, CategoryView> categories = categoryMap();
        List<ServiceItem> itemList = items.findByRequestIdOrderByIdAsc(r.getId());
        List<Offer> offerList = offers.findByRequestId(r.getId());
        Map<Long, Offer> offerById = offerList.stream().collect(Collectors.toMap(Offer::getId, Function.identity()));
        List<OfferLine> lineList = offerList.isEmpty() ? List.of() : lines.findByOfferIdIn(offerById.keySet());
        Map<Long, List<PartView>> partsByLine = lineList.isEmpty() ? Map.of()
                : parts.findByLineIdIn(lineList.stream().map(OfferLine::getId).toList()).stream()
                .collect(Collectors.groupingBy(OfferLinePart::getLineId, Collectors.mapping(PartView::of, Collectors.toList())));
        List<Long> providerIds = offerList.stream().map(Offer::getProviderId).distinct().toList();
        Map<Long, Provider> providerById = providers.findAllById(providerIds).stream()
                .collect(Collectors.toMap(Provider::getId, Function.identity()));
        Map<Long, ProviderStats.Summary> summaries = stats.summaries(providerIds);
        Map<Long, ProviderBranch> branchById = branches.findAllById(offerList.stream().map(Offer::getBranchId).distinct().toList())
                .stream().collect(Collectors.toMap(ProviderBranch::getId, Function.identity()));

        List<ItemView> itemViews = itemList.stream().map(item -> {
            List<OfferLineForCustomer> forItem = latestPerOffer(lineList, item.getId()).stream()
                    .filter(l -> l.getStatus() != OfferLine.Status.UNAVAILABLE)
                    .sorted(Comparator.comparing((OfferLine l) -> l.getStatus() != OfferLine.Status.PENDING)
                            .thenComparing(OfferLine::getCustomerTotal))
                    .map(l -> {
                        Offer o = offerById.get(l.getOfferId());
                        Provider p = providerById.get(o.getProviderId());
                        ProviderStats.Summary s = summaries.getOrDefault(p.getId(), ProviderStats.Summary.EMPTY);
                        return new OfferLineForCustomer(l.getId(), o.getId(), l.getStatus(),
                                new ProviderCard(p.getId(), p.getBusinessName(), s.ratingAverage(), s.ratingCount(), s.completedOrders()),
                                branchCard(branchById.get(o.getBranchId()), r), Pricing.of(l),
                                partsByLine.getOrDefault(l.getId(), List.of()), l.getEstimatedMinutes(), l.getProposedStart(),
                                l.getNotes(), Warranty.of(l), o.getExpiresAt(), l.getCreatedAt());
                    }).toList();
            return new ItemView(item.getId(), categories.get(item.getCategoryId()), item.getDescription(), item.getStatus(),
                    item.getOrderId(), forItem);
        }).toList();
        boolean editable = r.isOpen() && lineList.isEmpty();
        return new RequestDetail(r.getId(), r.getStatus(), VehicleView.of(r), r.getAreaId(), r.getAddress(), r.getLatitude(),
                r.getLongitude(), r.getPreferredDate(), r.getPreferredTimeFrom(), r.getPreferredTimeTo(), r.getNotes(),
                KeyList.split(r.getPhotoKeys()), r.getResponseDeadline(), editable, targets.countByRequestId(r.getId()),
                r.getCreatedAt(), itemViews);
    }

    /** A resubmitted offer keeps its old lines for history; the customer sees only the newest line per offer. */
    private static List<OfferLine> latestPerOffer(List<OfferLine> all, Long itemId) {
        return all.stream().filter(l -> l.getServiceItemId().equals(itemId))
                .collect(Collectors.toMap(OfferLine::getOfferId, Function.identity(), (a, b) -> a.getId() > b.getId() ? a : b))
                .values().stream().toList();
    }

    private PageResponse<RequestSummary> summaries(org.springframework.data.domain.Page<ServiceRequest> page) {
        Map<Long, CategoryView> categories = categoryMap();
        List<Long> ids = page.getContent().stream().map(ServiceRequest::getId).toList();
        Map<Long, List<ServiceItem>> itemsByRequest = ids.isEmpty() ? Map.of()
                : items.findByRequestIdIn(ids).stream().collect(Collectors.groupingBy(ServiceItem::getRequestId));
        return PageResponse.of(page, r -> {
            List<ServiceItem> list = itemsByRequest.getOrDefault(r.getId(), List.of());
            List<Offer> offerList = offers.findByRequestId(r.getId());
            long offerCount = offerList.stream().filter(o -> o.getStatus() == Offer.Status.ACTIVE).count();
            long accepted = list.stream().filter(i -> i.getStatus() == ServiceItem.Status.ACCEPTED).count();
            return new RequestSummary(r.getId(), r.getStatus(), r.vehicleLabel(), r.getAreaId(), list.size(),
                    list.stream().map(i -> categories.get(i.getCategoryId()).code()).toList(), offerCount, accepted,
                    r.getResponseDeadline(), r.getCreatedAt());
        });
    }

    private Map<Long, CategoryView> categoryMap() {
        return catalog.allCategories().stream().collect(Collectors.toMap(CategoryView::id, Function.identity()));
    }

    private static BranchCard branchCard(ProviderBranch b, ServiceRequest r) {
        Double d = Geo.distanceKm(r.getLatitude(), r.getLongitude(), b.getLatitude(), b.getLongitude());
        return new BranchCard(b.getId(), b.getName(), b.getAddress(), b.getAreaId(), b.getLatitude(), b.getLongitude(),
                d == null ? null : BigDecimal.valueOf(d).setScale(1, RoundingMode.HALF_UP));
    }
}
