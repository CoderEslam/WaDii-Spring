package com.doubleclick.wadii.admin;

import com.doubleclick.wadii.catalog.CatalogService;
import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.common.security.Permissions;
import com.doubleclick.wadii.dispute.Dispute;
import com.doubleclick.wadii.dispute.DisputeRecorder;
import com.doubleclick.wadii.dispute.DisputeService;
import com.doubleclick.wadii.fraud.FraudFlag;
import com.doubleclick.wadii.fraud.FraudService;
import com.doubleclick.wadii.identity.AdminRole;
import com.doubleclick.wadii.listing.Listing;
import com.doubleclick.wadii.listing.ListingService;
import com.doubleclick.wadii.order.OrderQueryService;
import com.doubleclick.wadii.order.OrderService;
import com.doubleclick.wadii.order.ServiceOrder;
import com.doubleclick.wadii.request.RequestQueryService;
import com.doubleclick.wadii.request.ServiceRequest;
import com.doubleclick.wadii.settings.ConfigKey;
import com.doubleclick.wadii.settings.PlatformConfigService;
import com.doubleclick.wadii.warranty.WarrantyClaim;
import com.doubleclick.wadii.warranty.WarrantyService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** Admin: marketplace configuration, operations, trust and content moderation. */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminOperationsController {

    private final CatalogService catalog;
    private final PlatformConfigService config;
    private final RequestQueryService requests;
    private final OrderQueryService orderQuery;
    private final OrderService orders;
    private final DisputeService disputes;
    private final WarrantyService warranties;
    private final ListingService listings;
    private final AdminRatingService ratings;
    private final FraudService fraud;

    public record ConfigInput(@NotNull JsonNode payload, Instant effectiveFrom, @NotBlank @Size(max = 500) String reason) {
    }

    public record ConfigHistory(PlatformConfigService.VersionView current, List<PlatformConfigService.VersionView> history) {
    }

    public record NoteInput(@NotBlank @Size(max = 1000) String note) {
    }

    public record ManualFlagInput(@NotNull Long subjectUserId, @NotBlank @Size(max = 1000) String note) {
    }

    // ------------------------------------------------------------------ configuration

    @GetMapping("/categories")
    @PreAuthorize(Permissions.ADMIN)
    public ResponseEntity<ApiResponse<List<CatalogService.CategoryView>>> categories() {
        return ApiResponse.ok(catalog.allCategories());
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize(Permissions.SUPER_ADMIN)
    public ResponseEntity<ApiResponse<CatalogService.CategoryView>> category(@PathVariable Long id,
                                                                             @Valid @RequestBody CatalogService.CategoryUpdate body) {
        return ApiResponse.ok(catalog.updateCategory(id, body));
    }

    @GetMapping("/config/{key}")
    @PreAuthorize(Permissions.ADMIN)
    public ResponseEntity<ApiResponse<ConfigHistory>> config(@PathVariable ConfigKey key) {
        return ApiResponse.ok(new ConfigHistory(config.currentView(key), config.history(key)));
    }

    /** Marketplace rules need a super admin; financial groups need a finance admin (checked by the service). */
    @PostMapping("/config/{key}")
    @PreAuthorize(Permissions.ADMIN)
    public ResponseEntity<ApiResponse<PlatformConfigService.VersionView>> publish(@AuthenticationPrincipal AuthUser me,
                                                                                  @PathVariable ConfigKey key,
                                                                                  @Valid @RequestBody ConfigInput body) {
        if (!key.financial() && !me.hasAdminRole(AdminRole.SUPER_ADMIN)) {
            throw BusinessException.forbidden("Marketplace settings require the super admin role.");
        }
        return ApiResponse.created(config.publish(me, key, body.payload(), body.effectiveFrom(), body.reason()), "New version saved");
    }

    // ------------------------------------------------------------------ requests and orders

    @GetMapping("/requests")
    @PreAuthorize(Permissions.SUPPORT_ADMIN)
    public ResponseEntity<ApiResponse<PageResponse<RequestQueryService.RequestSummary>>> requests(
            @RequestParam(required = false) ServiceRequest.Status status,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(requests.adminList(status, page, size));
    }

    @GetMapping("/orders")
    @PreAuthorize(Permissions.SUPPORT_OR_FINANCE)
    public ResponseEntity<ApiResponse<PageResponse<OrderQueryService.OrderSummary>>> orders(
            @RequestParam(required = false) ServiceOrder.Status status, @RequestParam(required = false) ServiceOrder.CashStatus cashStatus,
            @RequestParam(required = false) Long providerId, @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(orderQuery.adminList(status, cashStatus, providerId, categoryId, page, size));
    }

    @PostMapping("/orders/{id}/cancel")
    @PreAuthorize(Permissions.SUPPORT_ADMIN)
    public ResponseEntity<ApiResponse<OrderQueryService.OrderDetail>> cancelOrder(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                                 @Valid @RequestBody AdminUserService.ReasonInput body) {
        orders.adminCancel(me, id, body.reason());
        return ApiResponse.ok(orderQuery.detail(me, id));
    }

    // ------------------------------------------------------------------ disputes and warranty

    @GetMapping("/disputes")
    @PreAuthorize(Permissions.SUPPORT_OR_FINANCE)
    public ResponseEntity<ApiResponse<PageResponse<DisputeRecorder.View>>> disputes(
            @RequestParam(required = false) Dispute.Status status, @RequestParam(required = false) Dispute.Type type,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(disputes.adminList(status, type, page, size));
    }

    @GetMapping("/disputes/{id}")
    @PreAuthorize(Permissions.SUPPORT_OR_FINANCE)
    public ResponseEntity<ApiResponse<DisputeRecorder.View>> dispute(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(disputes.detail(me, id));
    }

    @PostMapping("/disputes/{id}/assign")
    @PreAuthorize(Permissions.SUPPORT_OR_FINANCE)
    public ResponseEntity<ApiResponse<DisputeRecorder.View>> assign(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(disputes.assign(me, id));
    }

    @PostMapping("/disputes/{id}/resolve")
    @PreAuthorize(Permissions.SUPPORT_OR_FINANCE)
    public ResponseEntity<ApiResponse<DisputeRecorder.View>> resolve(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                     @Valid @RequestBody DisputeService.ResolveInput body) {
        return ApiResponse.ok(disputes.resolve(me, id, body));
    }

    @PostMapping("/disputes/{id}/reject")
    @PreAuthorize(Permissions.SUPPORT_ADMIN)
    public ResponseEntity<ApiResponse<DisputeRecorder.View>> reject(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                    @Valid @RequestBody NoteInput body) {
        return ApiResponse.ok(disputes.reject(me, id, body.note()));
    }

    @GetMapping("/warranty-claims")
    @PreAuthorize(Permissions.SUPPORT_ADMIN)
    public ResponseEntity<ApiResponse<PageResponse<WarrantyService.ClaimView>>> claims(
            @RequestParam(required = false) WarrantyClaim.Status status,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(warranties.adminClaims(status, page, size));
    }

    // ------------------------------------------------------------------ content and trust

    @GetMapping("/listings")
    @PreAuthorize(Permissions.CONTENT_ADMIN)
    public ResponseEntity<ApiResponse<PageResponse<ListingService.ListingView>>> listings(
            @AuthenticationPrincipal AuthUser me, @RequestParam(required = false) Listing.Status status,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(listings.adminList(me, status, page, size));
    }

    @PostMapping("/listings/{id}/approve")
    @PreAuthorize(Permissions.CONTENT_ADMIN)
    public ResponseEntity<ApiResponse<ListingService.ListingView>> approveListing(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(listings.approve(me, id));
    }

    @PostMapping("/listings/{id}/reject")
    @PreAuthorize(Permissions.CONTENT_ADMIN)
    public ResponseEntity<ApiResponse<ListingService.ListingView>> rejectListing(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                                @Valid @RequestBody AdminUserService.ReasonInput body) {
        return ApiResponse.ok(listings.reject(me, id, body.reason()));
    }

    @GetMapping("/ratings")
    @PreAuthorize(Permissions.CONTENT_ADMIN)
    public ResponseEntity<ApiResponse<PageResponse<OrderQueryService.RatingView>>> ratings(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(ratings.list(page, size));
    }

    @PostMapping("/ratings/{id}/hide")
    @PreAuthorize(Permissions.CONTENT_ADMIN)
    public ResponseEntity<ApiResponse<OrderQueryService.RatingView>> hide(@PathVariable Long id) {
        return ApiResponse.ok(ratings.setHidden(id, true));
    }

    @PostMapping("/ratings/{id}/unhide")
    @PreAuthorize(Permissions.CONTENT_ADMIN)
    public ResponseEntity<ApiResponse<OrderQueryService.RatingView>> unhide(@PathVariable Long id) {
        return ApiResponse.ok(ratings.setHidden(id, false));
    }

    @GetMapping("/fraud-flags")
    @PreAuthorize(Permissions.SUPPORT_ADMIN)
    public ResponseEntity<ApiResponse<PageResponse<FraudService.View>>> flags(
            @RequestParam(required = false) FraudFlag.Status status,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(fraud.list(status, page, size));
    }

    @PostMapping("/fraud-flags")
    @PreAuthorize(Permissions.SUPPORT_ADMIN)
    public ResponseEntity<ApiResponse<FraudService.View>> manualFlag(@AuthenticationPrincipal AuthUser me,
                                                                     @Valid @RequestBody ManualFlagInput body) {
        return ApiResponse.created(fraud.manual(me, body.subjectUserId(), body.note()), "Flag created");
    }

    @PostMapping("/fraud-flags/{id}/dismiss")
    @PreAuthorize(Permissions.SUPPORT_ADMIN)
    public ResponseEntity<ApiResponse<FraudService.View>> dismiss(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                  @Valid @RequestBody NoteInput body) {
        return ApiResponse.ok(fraud.review(me, id, FraudFlag.Status.DISMISSED, body.note()));
    }

    @PostMapping("/fraud-flags/{id}/action")
    @PreAuthorize(Permissions.SUPPORT_ADMIN)
    public ResponseEntity<ApiResponse<FraudService.View>> action(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                 @Valid @RequestBody NoteInput body) {
        return ApiResponse.ok(fraud.review(me, id, FraudFlag.Status.ACTIONED, body.note()));
    }
}
