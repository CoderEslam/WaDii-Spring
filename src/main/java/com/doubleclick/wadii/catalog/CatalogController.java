package com.doubleclick.wadii.catalog;

import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.Permissions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Reference data. Reads are public (needed before registration); writes need CONTENT_ADMIN.
 * Catalog rows are small, bounded reference lists, so reads are not paginated.
 */
@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CountryRepository countryRepository;
    private final ProvinceRepository provinceRepository;
    private final CityRepository cityRepository;
    private final CatalogServiceRepository serviceRepository;
    private final CarTypeRepository carTypeRepository;
    private final CommonSparePartRepository sparePartRepository;
    private final CancellationReasonRepository reasonRepository;

    public record Item(Long id, String name) {
    }

    public record ServiceItem(Long id, String name, String description, boolean active) {
        static ServiceItem of(CatalogService s) {
            return new ServiceItem(s.getId(), s.getName(), s.getDescription(), s.isActive());
        }
    }

    public record ReasonItem(Long id, String text, ReasonAppliesTo appliesTo) {
        static ReasonItem of(CancellationReason r) {
            return new ReasonItem(r.getId(), r.getText(), r.getAppliesTo());
        }
    }

    public record NameRequest(@NotBlank @Size(max = 150) String name, Long parentId) {
    }

    public record ServiceRequest(@NotBlank @Size(max = 120) String name, @Size(max = 2000) String description, Boolean active) {
    }

    public record ReasonRequest(@NotBlank @Size(max = 255) String text, @NotNull ReasonAppliesTo appliesTo, Boolean active) {
    }

    // ---------------------------------------------------------------- reads (public)

    @GetMapping("/countries")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<Item>>> countries() {
        return ApiResponse.ok(countryRepository.findAllByOrderByNameAsc().stream().map(c -> new Item(c.getId(), c.getName())).toList());
    }

    @GetMapping("/countries/{countryId}/provinces")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<Item>>> provinces(@PathVariable Long countryId) {
        return ApiResponse.ok(provinceRepository.findByCountryIdOrderByNameAsc(countryId).stream().map(p -> new Item(p.getId(), p.getName())).toList());
    }

    @GetMapping("/provinces/{provinceId}/cities")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<Item>>> cities(@PathVariable Long provinceId) {
        return ApiResponse.ok(cityRepository.findByProvinceIdOrderByNameAsc(provinceId).stream().map(c -> new Item(c.getId(), c.getName())).toList());
    }

    @GetMapping("/services")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<ServiceItem>>> services(@RequestParam(defaultValue = "false") boolean includeInactive) {
        var list = includeInactive ? serviceRepository.findAll() : serviceRepository.findByActiveTrueOrderByNameAsc();
        return ApiResponse.ok(list.stream().map(ServiceItem::of).toList());
    }

    @GetMapping("/car-types")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<Item>>> carTypes() {
        return ApiResponse.ok(carTypeRepository.findAllByOrderByNameAsc().stream().map(c -> new Item(c.getId(), c.getName())).toList());
    }

    @GetMapping("/spare-parts")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<Item>>> spareParts() {
        return ApiResponse.ok(sparePartRepository.findAllByOrderByNameAsc().stream().map(c -> new Item(c.getId(), c.getName())).toList());
    }

    @GetMapping("/cancellation-reasons")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<ReasonItem>>> reasons() {
        return ApiResponse.ok(reasonRepository.findByActiveTrue().stream().map(ReasonItem::of).toList());
    }

    // ---------------------------------------------------------------- writes (CONTENT_ADMIN)

    @PostMapping("/countries")
    @PreAuthorize(Permissions.CONTENT_ADMIN)
    @Transactional
    public ResponseEntity<ApiResponse<Item>> addCountry(@Valid @RequestBody NameRequest r) {
        Country c = new Country();
        c.setName(r.name().trim());
        c = countryRepository.save(c);
        return ApiResponse.created(new Item(c.getId(), c.getName()), "Country created");
    }

    @PostMapping("/provinces")
    @PreAuthorize(Permissions.CONTENT_ADMIN)
    @Transactional
    public ResponseEntity<ApiResponse<Item>> addProvince(@Valid @RequestBody NameRequest r) {
        Province p = new Province();
        p.setName(r.name().trim());
        p.setCountry(countryRepository.findById(required(r.parentId())).orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND)));
        p = provinceRepository.save(p);
        return ApiResponse.created(new Item(p.getId(), p.getName()), "Province created");
    }

    @PostMapping("/cities")
    @PreAuthorize(Permissions.CONTENT_ADMIN)
    @Transactional
    public ResponseEntity<ApiResponse<Item>> addCity(@Valid @RequestBody NameRequest r) {
        City c = new City();
        c.setName(r.name().trim());
        c.setProvince(provinceRepository.findById(required(r.parentId())).orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND)));
        c = cityRepository.save(c);
        return ApiResponse.created(new Item(c.getId(), c.getName()), "City created");
    }

    @PostMapping("/services")
    @PreAuthorize(Permissions.CONTENT_ADMIN)
    @Transactional
    public ResponseEntity<ApiResponse<ServiceItem>> addService(@Valid @RequestBody ServiceRequest r) {
        CatalogService s = new CatalogService();
        s.setName(r.name().trim());
        s.setDescription(r.description());
        s.setActive(r.active() == null || r.active());
        return ApiResponse.created(ServiceItem.of(serviceRepository.save(s)), "Service created");
    }

    /** Services are deactivated rather than deleted: orders and providers reference them. */
    @PutMapping("/services/{id}")
    @PreAuthorize(Permissions.CONTENT_ADMIN)
    @Transactional
    public ResponseEntity<ApiResponse<ServiceItem>> updateService(@PathVariable Long id, @Valid @RequestBody ServiceRequest r) {
        CatalogService s = serviceRepository.findById(id).orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND));
        s.setName(r.name().trim());
        s.setDescription(r.description());
        if (r.active() != null) s.setActive(r.active());
        return ApiResponse.ok(ServiceItem.of(s), "Service updated");
    }

    @PostMapping("/car-types")
    @PreAuthorize(Permissions.CONTENT_ADMIN)
    @Transactional
    public ResponseEntity<ApiResponse<Item>> addCarType(@Valid @RequestBody NameRequest r) {
        CarType c = new CarType();
        c.setName(r.name().trim());
        c = carTypeRepository.save(c);
        return ApiResponse.created(new Item(c.getId(), c.getName()), "Car type created");
    }

    @PostMapping("/spare-parts")
    @PreAuthorize(Permissions.CONTENT_ADMIN)
    @Transactional
    public ResponseEntity<ApiResponse<Item>> addSparePart(@Valid @RequestBody NameRequest r) {
        CommonSparePart c = new CommonSparePart();
        c.setName(r.name().trim());
        c = sparePartRepository.save(c);
        return ApiResponse.created(new Item(c.getId(), c.getName()), "Spare part created");
    }

    @PostMapping("/cancellation-reasons")
    @PreAuthorize(Permissions.CONTENT_ADMIN)
    @Transactional
    public ResponseEntity<ApiResponse<ReasonItem>> addReason(@Valid @RequestBody ReasonRequest r) {
        CancellationReason reason = new CancellationReason();
        reason.setText(r.text().trim());
        reason.setAppliesTo(r.appliesTo());
        reason.setActive(r.active() == null || r.active());
        return ApiResponse.created(ReasonItem.of(reasonRepository.save(reason)), "Reason created");
    }

    private static Long required(Long parentId) {
        if (parentId == null) throw BusinessException.validation("parentId is required");
        return parentId;
    }
}
