package com.doubleclick.wadii.admin;

import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.common.security.Permissions;
import com.doubleclick.wadii.identity.AccountStatus;
import com.doubleclick.wadii.provider.Provider;
import com.doubleclick.wadii.provider.ProviderViews.ApplicationView;
import jakarta.validation.Valid;
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

/** Admin: users, staff accounts, provider applications and provider activation. */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AdminUserService users;
    private final AdminProviderService providers;

    @GetMapping("/users")
    @PreAuthorize(Permissions.SUPPORT_ADMIN)
    public ResponseEntity<ApiResponse<PageResponse<AdminUserService.AdminUserView>>> users(
            @RequestParam(required = false) String q, @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(users.list(q, status, page, size));
    }

    @PostMapping("/users/{id}/suspend")
    @PreAuthorize(Permissions.SUPPORT_ADMIN)
    public ResponseEntity<ApiResponse<AdminUserService.AdminUserView>> suspend(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                              @Valid @RequestBody AdminUserService.ReasonInput body) {
        return ApiResponse.ok(users.changeStatus(me, id, AccountStatus.SUSPENDED, body.reason()));
    }

    @PostMapping("/users/{id}/ban")
    @PreAuthorize(Permissions.SUPPORT_ADMIN)
    public ResponseEntity<ApiResponse<AdminUserService.AdminUserView>> ban(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                          @Valid @RequestBody AdminUserService.ReasonInput body) {
        return ApiResponse.ok(users.changeStatus(me, id, AccountStatus.BANNED, body.reason()));
    }

    @PostMapping("/users/{id}/reactivate")
    @PreAuthorize(Permissions.SUPPORT_ADMIN)
    public ResponseEntity<ApiResponse<AdminUserService.AdminUserView>> reactivate(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                                 @Valid @RequestBody AdminUserService.ReasonInput body) {
        return ApiResponse.ok(users.changeStatus(me, id, AccountStatus.ACTIVE, body.reason()));
    }

    @PostMapping("/admins")
    @PreAuthorize(Permissions.SUPER_ADMIN)
    public ResponseEntity<ApiResponse<AdminUserService.AdminUserView>> createAdmin(@AuthenticationPrincipal AuthUser me,
                                                                                  @Valid @RequestBody AdminUserService.CreateAdminInput body) {
        return ApiResponse.created(users.createAdmin(me, body), "Staff account created");
    }

    @PutMapping("/admins/{id}/roles")
    @PreAuthorize(Permissions.SUPER_ADMIN)
    public ResponseEntity<ApiResponse<AdminUserService.AdminUserView>> roles(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                            @Valid @RequestBody AdminUserService.RolesInput body) {
        return ApiResponse.ok(users.setRoles(me, id, body.roles()));
    }

    @GetMapping("/provider-applications")
    @PreAuthorize(Permissions.SUPPORT_ADMIN)
    public ResponseEntity<ApiResponse<PageResponse<ApplicationView>>> applications(
            @RequestParam(required = false) Provider.ApplicationStatus status,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(providers.applications(status, page, size));
    }

    @GetMapping("/provider-applications/{id}")
    @PreAuthorize(Permissions.SUPPORT_ADMIN)
    public ResponseEntity<ApiResponse<ApplicationView>> application(@PathVariable Long id) {
        return ApiResponse.ok(providers.application(id));
    }

    @PostMapping("/provider-applications/{id}/start-review")
    @PreAuthorize(Permissions.SUPPORT_ADMIN)
    public ResponseEntity<ApiResponse<ApplicationView>> startReview(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(providers.startReview(me, id));
    }

    @PostMapping("/provider-applications/{id}/approve")
    @PreAuthorize(Permissions.SUPPORT_ADMIN)
    public ResponseEntity<ApiResponse<ApplicationView>> approve(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(providers.approve(me, id));
    }

    @PostMapping("/provider-applications/{id}/reject")
    @PreAuthorize(Permissions.SUPPORT_ADMIN)
    public ResponseEntity<ApiResponse<ApplicationView>> reject(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                               @Valid @RequestBody AdminUserService.ReasonInput body) {
        return ApiResponse.ok(providers.reject(me, id, body.reason()));
    }

    @GetMapping("/providers")
    @PreAuthorize(Permissions.SUPPORT_ADMIN)
    public ResponseEntity<ApiResponse<PageResponse<ApplicationView>>> providers(
            @RequestParam(required = false) String q, @RequestParam(required = false) Provider.Status status,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(providers.verified(q, status, page, size));
    }

    @PostMapping("/providers/{id}/suspend")
    @PreAuthorize(Permissions.SUPPORT_ADMIN)
    public ResponseEntity<ApiResponse<ApplicationView>> suspendProvider(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                        @Valid @RequestBody AdminUserService.ReasonInput body) {
        return ApiResponse.ok(providers.suspend(me, id, body.reason()));
    }

    @PostMapping("/providers/{id}/reactivate")
    @PreAuthorize(Permissions.SUPPORT_ADMIN)
    public ResponseEntity<ApiResponse<ApplicationView>> reactivateProvider(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                           @Valid @RequestBody AdminUserService.ReasonInput body) {
        return ApiResponse.ok(providers.reactivate(me, id, body.reason()));
    }
}
