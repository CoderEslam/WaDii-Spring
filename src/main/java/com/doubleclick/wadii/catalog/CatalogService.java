package com.doubleclick.wadii.catalog;

import com.doubleclick.wadii.audit.AuditService;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final AreaRepository areas;
    private final CategoryRepository categories;
    private final AuditService audit;

    public record CategoryView(Long id, String code, String nameEn, String nameAr, boolean vehicleRequired,
                               boolean active, int sortOrder) {
        public static CategoryView of(ServiceCategory c) {
            return new CategoryView(c.getId(), c.getCode(), c.getNameEn(), c.getNameAr(), c.isVehicleRequired(),
                    c.isActive(), c.getSortOrder());
        }
    }

    public record AreaView(Long id, String nameEn, String nameAr) {
    }

    public record CategoryUpdate(@NotBlank @Size(max = 80) String nameEn, @NotBlank @Size(max = 80) String nameAr,
                                 boolean vehicleRequired, boolean active, @Min(0) @Max(1000) int sortOrder) {
    }

    @Transactional(readOnly = true)
    public List<CategoryView> activeCategories() {
        return categories.findByActiveTrueOrderBySortOrderAscIdAsc().stream().map(CategoryView::of).toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryView> allCategories() {
        return categories.findAllByOrderBySortOrderAscIdAsc().stream().map(CategoryView::of).toList();
    }

    @Transactional(readOnly = true)
    public List<AreaView> areas() {
        return areas.findAllByOrderByIdAsc().stream().map(a -> new AreaView(a.getId(), a.getNameEn(), a.getNameAr())).toList();
    }

    @Transactional(readOnly = true)
    public ServiceCategory category(Long id) {
        return categories.findById(id).orElseThrow(() -> BusinessException.of(ErrorCode.CATEGORY_NOT_FOUND));
    }

    /** Resolves ids to active categories; any unknown or inactive id fails the whole call. */
    @Transactional(readOnly = true)
    public List<ServiceCategory> activeCategories(Collection<Long> ids) {
        List<ServiceCategory> found = categories.findAllById(ids);
        if (found.size() != ids.stream().distinct().count() || found.stream().anyMatch(c -> !c.isActive())) {
            throw BusinessException.of(ErrorCode.CATEGORY_NOT_FOUND);
        }
        return found;
    }

    @Transactional(readOnly = true)
    public void requireArea(Long areaId) {
        if (areaId == null || !areas.existsById(areaId)) {
            throw BusinessException.validation("Unknown areaId.");
        }
    }

    @Transactional
    public CategoryView updateCategory(Long id, CategoryUpdate input) {
        ServiceCategory c = category(id);
        c.setNameEn(input.nameEn().trim());
        c.setNameAr(input.nameAr().trim());
        c.setVehicleRequired(input.vehicleRequired());
        c.setActive(input.active());
        c.setSortOrder(input.sortOrder());
        audit.record("CATEGORY_UPDATED", "CATEGORY", id,
                "vehicleRequired=" + input.vehicleRequired() + ", active=" + input.active());
        return CategoryView.of(c);
    }
}
