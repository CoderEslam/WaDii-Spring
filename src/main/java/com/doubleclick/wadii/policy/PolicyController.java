package com.doubleclick.wadii.policy;

import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.common.security.Permissions;
import com.doubleclick.wadii.policy.PolicyService.PolicyTerms;
import com.doubleclick.wadii.policy.PolicyService.PolicyVersionRequest;
import com.doubleclick.wadii.provider.ProviderAccess;
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

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;
    private final ProviderAccess providerAccess;

    /** Platform defaults (what applies when a provider has published nothing). */
    @GetMapping("/policies/platform")
    public ResponseEntity<ApiResponse<List<PolicyTerms>>> platform() {
        return ApiResponse.ok(Arrays.stream(PolicyType.values()).map(policyService::platformDefault).toList());
    }

    /** Effective policies of a provider, shown to customers before they accept an offer. */
    @GetMapping("/providers/{providerId}/policies")
    public ResponseEntity<ApiResponse<List<PolicyTerms>>> providerPolicies(@PathVariable Long providerId) {
        providerAccess.require(providerId);
        return ApiResponse.ok(policyService.effectivePolicies(providerId));
    }

    @GetMapping("/providers/me/policies")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<List<PolicyTerms>>> mine(@AuthenticationPrincipal AuthUser me) {
        return ApiResponse.ok(policyService.effectivePolicies(providerAccess.requireOwn(me).getId()));
    }

    @PostMapping("/providers/me/policies/{type}/versions")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<PolicyTerms>> publish(@AuthenticationPrincipal AuthUser me, @PathVariable PolicyType type,
                                                            @RequestBody PolicyVersionRequest request) {
        return ApiResponse.created(policyService.publishVersion(me, type, request), "Policy version published");
    }

    @GetMapping("/providers/me/policies/{type}/versions")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<List<PolicyTerms>>> history(@AuthenticationPrincipal AuthUser me, @PathVariable PolicyType type) {
        return ApiResponse.ok(policyService.history(me, type));
    }
}
