package com.doubleclick.wadii.catalog;

import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.listing.ListingCategory;
import com.doubleclick.wadii.listing.ListingCondition;
import com.doubleclick.wadii.settings.Configs;
import com.doubleclick.wadii.settings.PlatformConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/** Public reference data and the configuration values clients need to explain rules to users. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalog;
    private final PlatformConfigService config;

    public record CommissionInfo(Configs.FeeMode mode, BigDecimal value, Configs.CommissionPayer payer) {
    }

    public record SellerInfo(int maxActiveListings, Configs.FeeMode feeMode, BigDecimal feeValue,
                             Configs.FeeTiming feeTiming, int listingDurationDays) {
    }

    public record PublicConfig(int responseDeadlineHours, int offerValidityDefaultHours, int offerValidityMinHours,
                               int offerValidityMaxHours, int noShowGraceMinutes, CommissionInfo commission,
                               SellerInfo seller, List<ListingCategory> listingCategories,
                               List<ListingCondition> listingConditions) {
    }

    @GetMapping("/catalog/categories")
    public ResponseEntity<ApiResponse<List<CatalogService.CategoryView>>> categories() {
        return ApiResponse.ok(catalog.activeCategories());
    }

    @GetMapping("/catalog/areas")
    public ResponseEntity<ApiResponse<List<CatalogService.AreaView>>> areas() {
        return ApiResponse.ok(catalog.areas());
    }

    @GetMapping("/config/public")
    public ResponseEntity<ApiResponse<PublicConfig>> publicConfig() {
        var m = config.marketplace();
        var c = config.commission().value();
        var s = config.seller().value();
        return ApiResponse.ok(new PublicConfig(m.responseDeadlineHours(), m.offerValidityDefaultHours(),
                m.offerValidityMinHours(), m.offerValidityMaxHours(), m.noShowGraceMinutes(),
                new CommissionInfo(c.mode(), c.value(), c.payer()),
                new SellerInfo(s.maxActiveListings(), s.feeMode(), s.feeValue(), s.feeTiming(), s.listingDurationDays()),
                Arrays.asList(ListingCategory.values()), Arrays.asList(ListingCondition.values())));
    }
}
