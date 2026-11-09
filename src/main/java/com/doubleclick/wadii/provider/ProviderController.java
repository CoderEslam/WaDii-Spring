package com.doubleclick.wadii.provider;

import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.provider.ProviderViews.ApplicationInput;
import com.doubleclick.wadii.provider.ProviderViews.ApplicationView;
import com.doubleclick.wadii.provider.ProviderViews.BranchInput;
import com.doubleclick.wadii.provider.ProviderViews.BranchView;
import com.doubleclick.wadii.provider.ProviderViews.DocumentInput;
import com.doubleclick.wadii.provider.ProviderViews.DocumentView;
import com.doubleclick.wadii.provider.ProviderViews.HoursRequest;
import com.doubleclick.wadii.provider.ProviderViews.PolicyInput;
import com.doubleclick.wadii.provider.ProviderViews.PolicyView;
import com.doubleclick.wadii.provider.ProviderViews.ProfilePatch;
import com.doubleclick.wadii.provider.ProviderViews.ServicesInput;
import com.doubleclick.wadii.provider.ProviderViews.VehicleSupportRequest;
import com.doubleclick.wadii.provider.ProviderViews.VehicleSupportView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Provider application and self-management (Provider Mode). */
@RestController
@RequestMapping("/api/v1/provider")
@RequiredArgsConstructor
public class ProviderController {

    private final ProviderService service;
    private final ProviderAccess access;

    @GetMapping("/application")
    public ResponseEntity<ApiResponse<ApplicationView>> application(@AuthenticationPrincipal AuthUser me) {
        return ApiResponse.ok(service.application(me));
    }

    @PutMapping("/application")
    public ResponseEntity<ApiResponse<ApplicationView>> saveApplication(@AuthenticationPrincipal AuthUser me,
                                                                        @Valid @RequestBody ApplicationInput body) {
        return ApiResponse.ok(service.saveApplication(me, body));
    }

    @PostMapping("/application/documents")
    public ResponseEntity<ApiResponse<DocumentView>> addDocument(@AuthenticationPrincipal AuthUser me,
                                                                 @Valid @RequestBody DocumentInput body) {
        return ApiResponse.created(service.addDocument(me, body), "Document added");
    }

    @DeleteMapping("/application/documents/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        service.deleteDocument(me, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/application/submit")
    public ResponseEntity<ApiResponse<ApplicationView>> submit(@AuthenticationPrincipal AuthUser me) {
        return ApiResponse.ok(service.submit(me));
    }

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<ApplicationView>> profile(@AuthenticationPrincipal AuthUser me) {
        return ApiResponse.ok(service.applicationView(access.own(me)));
    }

    @PatchMapping("/profile")
    public ResponseEntity<ApiResponse<ApplicationView>> patchProfile(@AuthenticationPrincipal AuthUser me,
                                                                     @Valid @RequestBody ProfilePatch body) {
        return ApiResponse.ok(service.patchProfile(me, body));
    }

    @PutMapping("/services")
    public ResponseEntity<ApiResponse<ApplicationView>> services(@AuthenticationPrincipal AuthUser me,
                                                                 @Valid @RequestBody ServicesInput body) {
        return ApiResponse.ok(service.setServices(me, body.categoryIds()));
    }

    @GetMapping("/branches")
    public ResponseEntity<ApiResponse<List<BranchView>>> branches(@AuthenticationPrincipal AuthUser me) {
        return ApiResponse.ok(service.branches(me));
    }

    @PostMapping("/branches")
    public ResponseEntity<ApiResponse<BranchView>> createBranch(@AuthenticationPrincipal AuthUser me,
                                                                @Valid @RequestBody BranchInput body) {
        return ApiResponse.created(service.createBranch(me, body), "Branch created");
    }

    @PutMapping("/branches/{id}")
    public ResponseEntity<ApiResponse<BranchView>> updateBranch(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                @Valid @RequestBody BranchInput body) {
        return ApiResponse.ok(service.updateBranch(me, id, body));
    }

    @PutMapping("/branches/{id}/hours")
    public ResponseEntity<ApiResponse<BranchView>> hours(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                         @Valid @RequestBody HoursRequest body) {
        return ApiResponse.ok(service.setHours(me, id, body.hours()));
    }

    @GetMapping("/vehicle-support")
    public ResponseEntity<ApiResponse<List<VehicleSupportView>>> vehicleSupport(@AuthenticationPrincipal AuthUser me) {
        return ApiResponse.ok(service.vehicleSupport(me));
    }

    @PutMapping("/vehicle-support")
    public ResponseEntity<ApiResponse<List<VehicleSupportView>>> setVehicleSupport(@AuthenticationPrincipal AuthUser me,
                                                                                   @Valid @RequestBody VehicleSupportRequest body) {
        return ApiResponse.ok(service.setVehicleSupport(me, body.items()));
    }

    @GetMapping("/policy")
    public ResponseEntity<ApiResponse<PolicyView>> policy(@AuthenticationPrincipal AuthUser me) {
        return ApiResponse.ok(service.policy(me));
    }

    @PutMapping("/policy")
    public ResponseEntity<ApiResponse<PolicyView>> savePolicy(@AuthenticationPrincipal AuthUser me,
                                                              @Valid @RequestBody PolicyInput body) {
        return ApiResponse.ok(service.savePolicy(me, body));
    }
}
