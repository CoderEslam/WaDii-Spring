package com.doubleclick.wadii.request;

import com.doubleclick.wadii.audit.AuditService;
import com.doubleclick.wadii.catalog.CatalogService;
import com.doubleclick.wadii.catalog.ServiceCategory;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.common.util.KeyList;
import com.doubleclick.wadii.files.FileAccessRule;
import com.doubleclick.wadii.files.FilePurpose;
import com.doubleclick.wadii.files.FileService;
import com.doubleclick.wadii.notification.NotificationService;
import com.doubleclick.wadii.notification.NotificationType;
import com.doubleclick.wadii.offer.OfferLine;
import com.doubleclick.wadii.offer.OfferLineRepository;
import com.doubleclick.wadii.offer.OfferStateService;
import com.doubleclick.wadii.provider.ProviderRepository;
import com.doubleclick.wadii.settings.PlatformConfigService;
import com.doubleclick.wadii.vehicle.Vehicle;
import com.doubleclick.wadii.vehicle.VehicleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Customer commands on parent requests and their service items. */
@Service
@RequiredArgsConstructor
public class RequestService implements FileAccessRule {

    public static final String FILE_REF = "REQUEST";

    private final ServiceRequestRepository requests;
    private final ServiceItemRepository items;
    private final RequestTargetRepository targets;
    private final OfferLineRepository lines;
    private final OfferStateService offerState;
    private final RequestStatusService status;
    private final MatchingService matching;
    private final CatalogService catalog;
    private final VehicleService vehicles;
    private final ProviderRepository providers;
    private final FileService files;
    private final NotificationService notifications;
    private final PlatformConfigService config;
    private final AuditService audit;
    private final Clock clock;

    public record VehicleChoice(Long vehicleId, @Size(max = 60) String make, @Size(max = 60) String model,
                                @Min(1950) @Max(2100) Integer year, boolean save) {
    }

    public record ItemInput(@NotNull Long categoryId, @Size(max = 2000) String description) {
    }

    public record RequestInput(@Valid VehicleChoice vehicle, @NotEmpty @Size(max = 7) List<@Valid ItemInput> items,
                               @NotNull Long areaId, @NotBlank @Size(max = 300) String address,
                               @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
                               @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
                               LocalDate preferredDate, LocalTime preferredTimeFrom, LocalTime preferredTimeTo,
                               @Size(max = 2000) String notes, @Size(max = 8) List<String> photoKeys, Long providerId) {
    }

    public record CancelInput(@Size(max = 500) String reason) {
    }

    @Transactional
    public ServiceRequest create(AuthUser customer, RequestInput input) {
        requireCustomer(customer);
        Map<Long, ServiceCategory> categories = validateItems(input.items());
        ServiceRequest request = new ServiceRequest();
        request.setCustomerId(customer.id());
        if (input.providerId() != null) {
            var provider = providers.findById(input.providerId()).orElseThrow(() -> BusinessException.of(ErrorCode.PROVIDER_NOT_FOUND));
            if (provider.getUserId().equals(customer.id())) {
                throw BusinessException.of(ErrorCode.SELF_DEALING_FORBIDDEN);
            }
            request.setExplicitProviderId(provider.getId());
        }
        apply(customer, request, input, categories);
        request.setResponseDeadline(Instant.now(clock).plus(Duration.ofHours(config.marketplace().responseDeadlineHours()))
                .truncatedTo(ChronoUnit.SECONDS));
        requests.save(request);
        List<ServiceItem> created = saveItems(request.getId(), input.items());
        files.attach(customer.id(), input.photoKeys(), FilePurpose.REQUEST_PHOTO, FILE_REF, request.getId());
        matching.match(request, created, categories);
        audit.record("REQUEST_CREATED", "REQUEST", request.getId(), created.size() + " items");
        return request;
    }

    /** Material changes are allowed only before any provider has responded, so nobody quotes a moving target. */
    @Transactional
    public ServiceRequest update(AuthUser customer, Long id, RequestInput input) {
        ServiceRequest request = ownLocked(customer, id);
        if (!request.isOpen() || lines.countForRequest(id) > 0) {
            throw BusinessException.of(ErrorCode.REQUEST_NOT_EDITABLE);
        }
        Map<Long, ServiceCategory> categories = validateItems(input.items());
        apply(customer, request, input, categories);
        targets.deleteByRequestId(id);
        items.deleteByRequestId(id);
        List<ServiceItem> created = saveItems(id, input.items());
        files.attach(customer.id(), input.photoKeys(), FilePurpose.REQUEST_PHOTO, FILE_REF, id);
        matching.match(request, created, categories);
        status.refresh(id);
        audit.record("REQUEST_EDITED", "REQUEST", id, null);
        return request;
    }

    @Transactional
    public ServiceRequest cancel(AuthUser customer, Long id, String reason) {
        ServiceRequest request = ownLocked(customer, id);
        List<ServiceItem> open = items.findByRequestIdOrderByIdAsc(id).stream().filter(ServiceItem::isOpen).toList();
        if (open.isEmpty()) {
            throw BusinessException.of(ErrorCode.ITEM_NOT_OPEN, "Nothing left to cancel on this request.");
        }
        cancelItems(open, "The customer canceled the request.");
        request.setCancelReason(reason);
        audit.record("REQUEST_CANCELED", "REQUEST", id, reason);
        return status.refresh(id);
    }

    @Transactional
    public ServiceRequest cancelItem(AuthUser customer, Long id, Long itemId) {
        ownLocked(customer, id);
        ServiceItem item = items.lockById(itemId).filter(i -> i.getRequestId().equals(id))
                .orElseThrow(() -> BusinessException.of(ErrorCode.NOT_FOUND));
        if (!item.isOpen()) {
            throw BusinessException.of(ErrorCode.ITEM_NOT_OPEN);
        }
        cancelItems(List.of(item), "The customer no longer needs this service.");
        audit.record("REQUEST_ITEM_CANCELED", "REQUEST", id, "item " + itemId);
        return status.refresh(id);
    }

    @Transactional
    public ServiceRequest declineLine(AuthUser customer, Long id, Long lineId) {
        ownLocked(customer, id);
        OfferLine line = lines.findById(lineId).orElseThrow(() -> BusinessException.of(ErrorCode.OFFER_NOT_FOUND));
        ServiceItem item = items.findById(line.getServiceItemId()).orElseThrow();
        if (!item.getRequestId().equals(id)) {
            throw BusinessException.of(ErrorCode.OFFER_NOT_FOUND);
        }
        if (line.getStatus() != OfferLine.Status.PENDING) {
            throw BusinessException.of(ErrorCode.OFFER_NOT_PENDING);
        }
        line.setStatus(OfferLine.Status.DECLINED);
        var offer = offerState.refresh(line.getOfferId());
        providers.findById(offer.getProviderId()).ifPresent(p -> notifications.notify(p.getUserId(),
                NotificationType.OFFER_DECLINED, "Offer declined", "The customer declined your offer for one service.",
                "OFFER", offer.getId()));
        return status.refresh(id);
    }

    @Transactional(readOnly = true)
    public ServiceRequest visible(AuthUser user, Long id) {
        ServiceRequest request = requests.findById(id).orElseThrow(() -> BusinessException.of(ErrorCode.REQUEST_NOT_FOUND));
        if (!request.getCustomerId().equals(user.id()) && !user.admin()) {
            throw BusinessException.of(ErrorCode.REQUEST_NOT_FOUND);
        }
        return request;
    }

    @Override
    public boolean supports(String refType) {
        return FILE_REF.equals(refType);
    }

    /** Request photos are visible to the customer and to invited providers. */
    @Override
    public boolean canRead(AuthUser user, String refType, Long refId) {
        return requests.findById(refId).map(r -> r.getCustomerId().equals(user.id())
                || providers.findByUserId(user.id()).flatMap(p -> targets.findByRequestIdAndProviderId(refId, p.getId())).isPresent())
                .orElse(false);
    }

    private void cancelItems(List<ServiceItem> list, String providerMessage) {
        list.forEach(i -> i.setStatus(ServiceItem.Status.CANCELED));
        offerState.closePendingLines(list.stream().map(ServiceItem::getId).toList(), OfferLine.Status.DECLINED, providerMessage);
    }

    private ServiceRequest ownLocked(AuthUser customer, Long id) {
        ServiceRequest request = requests.lockById(id).orElseThrow(() -> BusinessException.of(ErrorCode.REQUEST_NOT_FOUND));
        if (!request.getCustomerId().equals(customer.id())) {
            throw BusinessException.of(ErrorCode.REQUEST_NOT_FOUND);
        }
        return request;
    }

    private Map<Long, ServiceCategory> validateItems(List<ItemInput> input) {
        List<Long> ids = input.stream().map(ItemInput::categoryId).toList();
        if (ids.stream().distinct().count() != ids.size()) {
            throw BusinessException.validation("Each service category can appear only once in a request.");
        }
        return catalog.activeCategories(ids).stream().collect(Collectors.toMap(ServiceCategory::getId, Function.identity()));
    }

    private void apply(AuthUser customer, ServiceRequest request, RequestInput input, Map<Long, ServiceCategory> categories) {
        catalog.requireArea(input.areaId());
        if ((input.latitude() == null) != (input.longitude() == null)) {
            throw BusinessException.validation("latitude and longitude must be sent together.");
        }
        if (input.preferredTimeFrom() != null && input.preferredTimeTo() != null
                && !input.preferredTimeTo().isAfter(input.preferredTimeFrom())) {
            throw BusinessException.validation("preferredTimeTo must be after preferredTimeFrom.");
        }
        boolean vehicleRequired = categories.values().stream().anyMatch(ServiceCategory::isVehicleRequired);
        applyVehicle(customer, request, input.vehicle(), vehicleRequired);
        request.setAreaId(input.areaId());
        request.setAddress(input.address().trim());
        request.setLatitude(input.latitude());
        request.setLongitude(input.longitude());
        request.setPreferredDate(input.preferredDate());
        request.setPreferredTimeFrom(input.preferredTimeFrom());
        request.setPreferredTimeTo(input.preferredTimeTo());
        request.setNotes(input.notes());
        request.setPhotoKeys(KeyList.join(input.photoKeys()));
    }

    /** The vehicle may be a saved one, a new one (optionally saved) or none when no chosen service needs it. */
    private void applyVehicle(AuthUser customer, ServiceRequest request, VehicleChoice choice, boolean required) {
        request.setVehicleId(null);
        request.setVehicleMake(null);
        request.setVehicleModel(null);
        request.setVehicleYear(null);
        if (choice == null || (choice.vehicleId() == null && choice.make() == null)) {
            if (required) {
                throw BusinessException.of(ErrorCode.VEHICLE_REQUIRED);
            }
            return;
        }
        Vehicle vehicle;
        if (choice.vehicleId() != null) {
            vehicle = vehicles.own(customer.id(), choice.vehicleId());
        } else {
            if (choice.make() == null || choice.make().isBlank() || choice.model() == null || choice.model().isBlank()
                    || choice.year() == null) {
                throw BusinessException.validation("vehicle needs make, model and year.");
            }
            if (!choice.save()) {
                request.setVehicleMake(choice.make().trim());
                request.setVehicleModel(choice.model().trim());
                request.setVehicleYear(choice.year());
                return;
            }
            var saved = vehicles.create(customer.id(), new VehicleService.VehicleInput(choice.make(), choice.model(), choice.year()));
            vehicle = vehicles.own(customer.id(), saved.id());
        }
        vehicles.markUsed(vehicle);
        request.setVehicleId(vehicle.getId());
        request.setVehicleMake(vehicle.getMake());
        request.setVehicleModel(vehicle.getModel());
        request.setVehicleYear(vehicle.getYear());
    }

    private List<ServiceItem> saveItems(Long requestId, List<ItemInput> input) {
        return input.stream().map(i -> {
            ServiceItem item = new ServiceItem();
            item.setRequestId(requestId);
            item.setCategoryId(i.categoryId());
            item.setDescription(i.description());
            return items.save(item);
        }).toList();
    }

    private static void requireCustomer(AuthUser user) {
        if (user.admin()) {
            throw BusinessException.forbidden("Staff accounts cannot create service requests.");
        }
    }
}
