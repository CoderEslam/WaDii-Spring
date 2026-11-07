package com.doubleclick.wadii.listing;

import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.ledger.LedgerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/** Individual seller module: public browsing plus the seller's own listings and fees. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ListingController {

    private final ListingService service;
    private final LedgerService ledger;

    @GetMapping("/listings")
    public ResponseEntity<ApiResponse<PageResponse<ListingService.ListingView>>> browse(
            @AuthenticationPrincipal AuthUser me, @RequestParam(required = false) ListingCategory category,
            @RequestParam(required = false) Long areaId, @RequestParam(required = false) String q,
            @RequestParam(required = false) BigDecimal minPrice, @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.browse(me, category, areaId, q, minPrice, maxPrice, page, size));
    }

    @GetMapping("/listings/{id}")
    public ResponseEntity<ApiResponse<ListingService.ListingView>> detail(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(service.detail(me, id));
    }

    @PostMapping("/listings")
    public ResponseEntity<ApiResponse<ListingService.ListingView>> create(@AuthenticationPrincipal AuthUser me,
                                                                          @Valid @RequestBody ListingService.ListingInput body) {
        return ApiResponse.created(service.create(me, body), "Draft saved");
    }

    @PutMapping("/listings/{id}")
    public ResponseEntity<ApiResponse<ListingService.ListingView>> update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                          @Valid @RequestBody ListingService.ListingInput body) {
        return ApiResponse.ok(service.update(me, id, body));
    }

    @PostMapping("/listings/{id}/submit")
    public ResponseEntity<ApiResponse<ListingService.ListingView>> submit(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(service.submit(me, id));
    }

    @PostMapping("/listings/{id}/mark-sold")
    public ResponseEntity<ApiResponse<ListingService.ListingView>> sold(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(service.markSold(me, id));
    }

    @PostMapping("/listings/{id}/close")
    public ResponseEntity<ApiResponse<ListingService.ListingView>> close(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(service.close(me, id));
    }

    @GetMapping("/me/listings")
    public ResponseEntity<ApiResponse<PageResponse<ListingService.ListingView>>> mine(@AuthenticationPrincipal AuthUser me,
                                                                                     @RequestParam(required = false) Listing.Status status,
                                                                                     @RequestParam(required = false) Integer page,
                                                                                     @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.mine(me, status, page, size));
    }

    @GetMapping("/me/seller")
    public ResponseEntity<ApiResponse<ListingService.SellerSummary>> seller(@AuthenticationPrincipal AuthUser me) {
        return ApiResponse.ok(service.seller(me));
    }

    @PostMapping("/me/ledger/{id}/report-paid")
    public ResponseEntity<ApiResponse<LedgerService.View>> reportPaid(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                      @Valid @RequestBody LedgerService.ReportPaidRequest body) {
        return ApiResponse.ok(ledger.reportPaid(me, id, body));
    }
}
