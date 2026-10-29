package com.doubleclick.wadii.provider;

import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.common.security.Permissions;
import com.doubleclick.wadii.provider.dto.ProviderDtos.*;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/providers")
@PreAuthorize(Permissions.SUPPORT_ADMIN)
@RequiredArgsConstructor
public class AdminProviderController {

    private final ProviderService providerService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProviderOwnView>>> list(@RequestParam(required = false) VerificationStatus verificationStatus,
                                                                           @RequestParam(required = false) ProviderStatus status,
                                                                           @RequestParam(required = false) Integer page,
                                                                           @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(providerService.adminList(verificationStatus, status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProviderOwnView>> get(@PathVariable Long id) {
        return ApiResponse.ok(providerService.adminGet(id));
    }

    @GetMapping("/{id}/documents")
    public ResponseEntity<ApiResponse<List<DocumentView>>> documents(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(providerService.documents(me, id));
    }

    @PostMapping("/{id}/verification")
    public ResponseEntity<ApiResponse<ProviderOwnView>> verify(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                               @Valid @RequestBody VerificationDecision decision) {
        return ApiResponse.ok(providerService.verify(me, id, decision), "Verification updated");
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<ApiResponse<StatusChangeResult>> status(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                  @Valid @RequestBody StatusDecision decision) {
        return ApiResponse.ok(providerService.changeStatus(me, id, decision), "Status updated");
    }
}
