package com.doubleclick.wadii.warranty;

import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.common.security.Permissions;
import com.doubleclick.wadii.warranty.WarrantyService.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WarrantyController {

    private final WarrantyService warrantyService;

    @GetMapping("/orders/{orderId}/warranty")
    public ResponseEntity<ApiResponse<WarrantyView>> warranty(@AuthenticationPrincipal AuthUser me, @PathVariable Long orderId) {
        return ApiResponse.ok(warrantyService.forOrder(me, orderId));
    }

    @GetMapping("/orders/{orderId}/warranty/claims")
    public ResponseEntity<ApiResponse<List<ClaimView>>> claims(@AuthenticationPrincipal AuthUser me, @PathVariable Long orderId) {
        return ApiResponse.ok(warrantyService.claims(me, orderId));
    }

    @PostMapping("/orders/{orderId}/warranty/claims")
    public ResponseEntity<ApiResponse<ClaimView>> claim(@AuthenticationPrincipal AuthUser me, @PathVariable Long orderId,
                                                        @Valid @RequestBody ClaimRequest request) {
        return ApiResponse.created(warrantyService.claim(me, orderId, request), "Warranty claim submitted");
    }

    @PostMapping("/warranty-claims/{claimId}/decision")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<ClaimView>> decide(@AuthenticationPrincipal AuthUser me, @PathVariable Long claimId,
                                                         @Valid @RequestBody ClaimDecision decision) {
        return ApiResponse.ok(warrantyService.decideClaim(me, claimId, decision), "Decision saved");
    }

    @PostMapping("/warranty-claims/{claimId}/resolve")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<ClaimView>> resolve(@AuthenticationPrincipal AuthUser me, @PathVariable Long claimId) {
        return ApiResponse.ok(warrantyService.resolveClaim(me, claimId), "Claim resolved");
    }

    @PostMapping("/warranty-claims/{claimId}/escalate")
    public ResponseEntity<ApiResponse<ClaimView>> escalate(@AuthenticationPrincipal AuthUser me, @PathVariable Long claimId) {
        return ApiResponse.ok(warrantyService.escalate(me, claimId), "Claim escalated to a dispute");
    }

    @GetMapping("/orders/{orderId}/returns")
    public ResponseEntity<ApiResponse<List<ReturnView>>> returns(@AuthenticationPrincipal AuthUser me, @PathVariable Long orderId) {
        return ApiResponse.ok(warrantyService.returns(me, orderId));
    }

    @PostMapping("/orders/{orderId}/returns")
    public ResponseEntity<ApiResponse<ReturnView>> requestReturn(@AuthenticationPrincipal AuthUser me, @PathVariable Long orderId,
                                                                 @Valid @RequestBody ReturnCreate request) {
        return ApiResponse.created(warrantyService.requestReturn(me, orderId, request), "Return requested");
    }

    @PostMapping("/returns/{returnId}/decision")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<ReturnView>> decideReturn(@AuthenticationPrincipal AuthUser me, @PathVariable Long returnId,
                                                                @Valid @RequestBody ReturnDecision decision) {
        return ApiResponse.ok(warrantyService.decideReturn(me, returnId, decision), "Decision saved");
    }

    @PostMapping("/returns/{returnId}/received")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<ReturnView>> received(@AuthenticationPrincipal AuthUser me, @PathVariable Long returnId,
                                                            @Valid @RequestBody(required = false) ReturnReceived request) {
        return ApiResponse.ok(warrantyService.markReceived(me, returnId, request), "Return received");
    }

    @PostMapping("/returns/{returnId}/close")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<ReturnView>> close(@AuthenticationPrincipal AuthUser me, @PathVariable Long returnId) {
        return ApiResponse.ok(warrantyService.closeReturn(me, returnId), "Return closed");
    }
}
