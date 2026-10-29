package com.doubleclick.wadii.provider;

import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.common.security.Permissions;
import com.doubleclick.wadii.payment.PaymentMethod;
import com.doubleclick.wadii.provider.dto.ProviderDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
public class ProviderController {

    private final ProviderService providerService;

    // ---------------------------------------------------------------- browse (any authenticated user)

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProviderPublicView>>> search(@RequestParam(required = false) Long serviceId,
                                                                                @RequestParam(required = false) String q,
                                                                                @RequestParam(required = false) Integer page,
                                                                                @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(providerService.search(serviceId, q, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProviderPublicView>> get(@PathVariable Long id) {
        return ApiResponse.ok(providerService.publicProfile(id));
    }

    // ---------------------------------------------------------------- become a provider

    @PostMapping("/apply")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ProviderOwnView>> apply(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ApplyRequest request) {
        return ApiResponse.created(providerService.apply(me, request), "Application submitted; log in again to continue as a provider");
    }

    // ---------------------------------------------------------------- own profile

    @GetMapping("/me")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<ProviderOwnView>> me(@AuthenticationPrincipal AuthUser me) {
        return ApiResponse.ok(providerService.me(me));
    }

    @PatchMapping("/me")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<ProviderOwnView>> update(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody UpdateProviderRequest request) {
        return ApiResponse.ok(providerService.update(me, request), "Profile updated");
    }

    @PutMapping("/me/services")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<ProviderOwnView>> services(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ServicesRequest request) {
        return ApiResponse.ok(providerService.setServices(me, request.serviceIds()), "Services updated");
    }

    @PutMapping("/me/availability")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<ProviderOwnView>> availability(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody AvailabilityRequest request) {
        return ApiResponse.ok(providerService.setAvailability(me, request.acceptingOrders()), "Availability updated");
    }

    @PutMapping("/me/links")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<ProviderOwnView>> links(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody LinksRequest request) {
        return ApiResponse.ok(providerService.setLinks(me, request.links()), "Links updated");
    }

    @PostMapping("/me/branches")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<BranchView>> addBranch(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody BranchRequest request) {
        return ApiResponse.created(providerService.addBranch(me, request), "Branch created");
    }

    @PatchMapping("/me/branches/{branchId}")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<BranchView>> updateBranch(@AuthenticationPrincipal AuthUser me, @PathVariable Long branchId,
                                                                @Valid @RequestBody BranchRequest request) {
        return ApiResponse.ok(providerService.updateBranch(me, branchId, request), "Branch updated");
    }

    @DeleteMapping("/me/branches/{branchId}")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<BranchView>> deactivateBranch(@AuthenticationPrincipal AuthUser me, @PathVariable Long branchId) {
        return ApiResponse.ok(providerService.deactivateBranch(me, branchId), "Branch deactivated");
    }

    @GetMapping("/me/payment-accounts")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<List<PaymentAccountView>>> paymentAccounts(@AuthenticationPrincipal AuthUser me) {
        return ApiResponse.ok(providerService.paymentAccounts(me));
    }

    @PutMapping("/me/payment-accounts/{method}")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<PaymentAccountView>> upsertPaymentAccount(@AuthenticationPrincipal AuthUser me, @PathVariable PaymentMethod method,
                                                                                @Valid @RequestBody PaymentAccountRequest request) {
        return ApiResponse.ok(providerService.upsertPaymentAccount(me, method, request), "Payment account saved");
    }

    @DeleteMapping("/me/payment-accounts/{method}")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<PaymentAccountView>> deactivatePaymentAccount(@AuthenticationPrincipal AuthUser me, @PathVariable PaymentMethod method) {
        return ApiResponse.ok(providerService.deactivatePaymentAccount(me, method), "Payment account deactivated");
    }

    @PostMapping(value = "/me/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<DocumentView>> uploadDocument(@AuthenticationPrincipal AuthUser me, @RequestParam DocumentType type,
                                                                    @RequestParam("file") MultipartFile file) {
        return ApiResponse.created(providerService.uploadDocument(me, type, file), "Document uploaded");
    }

    @GetMapping("/me/documents")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<List<DocumentView>>> documents(@AuthenticationPrincipal AuthUser me) {
        return ApiResponse.ok(providerService.documents(me, null));
    }

    /** Owner or support admin. */
    @GetMapping("/documents/{documentId}/file")
    public ResponseEntity<Resource> documentFile(@AuthenticationPrincipal AuthUser me, @PathVariable Long documentId) {
        return providerService.documentFile(me, documentId);
    }

    @GetMapping("/me/stats")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<StatsView>> stats(@AuthenticationPrincipal AuthUser me) {
        return ApiResponse.ok(providerService.stats(me));
    }
}
