package com.doubleclick.wadii.provider;

import com.doubleclick.wadii.catalog.CatalogService;
import com.doubleclick.wadii.catalog.CatalogService.CategoryView;
import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.identity.User;
import com.doubleclick.wadii.identity.UserRepository;
import com.doubleclick.wadii.order.OrderQueryService;
import com.doubleclick.wadii.order.ProviderStats;
import com.doubleclick.wadii.order.Rating;
import com.doubleclick.wadii.order.RatingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Public provider card used when customers compare offers or pick a provider explicitly. */
@RestController
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
public class PublicProviderController {

    private final ProviderRepository providers;
    private final ProviderCategoryRepository providerCategories;
    private final ProviderBranchRepository branches;
    private final RatingRepository ratings;
    private final UserRepository users;
    private final ProviderStats stats;
    private final CatalogService catalog;

    public record BranchCard(Long id, String name, Long areaId, String address, BigDecimal latitude, BigDecimal longitude, String phone) {
    }

    public record PublicProvider(Long id, String businessName, String description, Long areaId, List<CategoryView> categories,
                                 BigDecimal ratingAverage, long ratingCount, long completedOrders, List<BranchCard> branches,
                                 Instant memberSince) {
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PublicProvider>> provider(@PathVariable Long id) {
        Provider p = providers.findById(id).filter(Provider::canOperate)
                .orElseThrow(() -> BusinessException.of(ErrorCode.PROVIDER_NOT_FOUND));
        var ids = providerCategories.findByProviderId(id).stream().map(ProviderCategory::getCategoryId).toList();
        var summary = stats.summary(id);
        List<BranchCard> branchCards = branches.findByProviderIdOrderByIdAsc(id).stream().filter(ProviderBranch::isActive)
                .map(b -> new BranchCard(b.getId(), b.getName(), b.getAreaId(), b.getAddress(), b.getLatitude(), b.getLongitude(), b.getPhone()))
                .toList();
        return ApiResponse.ok(new PublicProvider(p.getId(), p.getBusinessName(), p.getDescription(), p.getAreaId(),
                catalog.allCategories().stream().filter(c -> ids.contains(c.id())).toList(), summary.ratingAverage(),
                summary.ratingCount(), summary.completedOrders(), branchCards, p.getVerifiedAt()));
    }

    @GetMapping("/{id}/ratings")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PageResponse<OrderQueryService.RatingView>>> ratings(@PathVariable Long id,
                                                                                          @RequestParam(required = false) Integer page,
                                                                                          @RequestParam(required = false) Integer size) {
        var result = ratings.findByProviderIdAndHiddenFalseOrderByIdDesc(id, PageResponse.pageable(page, size));
        Map<Long, String> names = users.findAllById(result.getContent().stream().map(Rating::getCustomerId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getFullName));
        return ApiResponse.ok(PageResponse.of(result, r -> OrderQueryService.ratingView(r, firstName(names.get(r.getCustomerId())))));
    }

    /** Public reviews show only the reviewer's first name. */
    private static String firstName(String fullName) {
        if (fullName == null) return "";
        int space = fullName.indexOf(' ');
        return space > 0 ? fullName.substring(0, space) : fullName;
    }
}
