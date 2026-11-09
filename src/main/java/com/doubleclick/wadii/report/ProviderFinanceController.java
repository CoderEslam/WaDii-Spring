package com.doubleclick.wadii.report;

import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.ledger.LedgerEntry;
import com.doubleclick.wadii.ledger.LedgerService;
import com.doubleclick.wadii.provider.Provider;
import com.doubleclick.wadii.provider.ProviderAccess;
import com.doubleclick.wadii.subscription.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Provider Mode: dashboard, subscription and amounts owed to WaDii. */
@RestController
@RequestMapping("/api/v1/provider")
@RequiredArgsConstructor
public class ProviderFinanceController {

    private final ProviderDashboardService dashboard;
    private final SubscriptionService subscriptions;
    private final LedgerService ledger;
    private final ProviderAccess access;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<ProviderDashboardService.Dashboard>> dashboard(@AuthenticationPrincipal AuthUser me,
                                                                                    @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok(dashboard.dashboard(me, days));
    }

    /** Available even while restricted, so the provider can see what to settle. */
    @GetMapping("/subscription")
    public ResponseEntity<ApiResponse<SubscriptionService.View>> subscription(@AuthenticationPrincipal AuthUser me) {
        Provider p = access.own(me);
        return ApiResponse.ok(subscriptions.view(p.getId()));
    }

    @GetMapping("/ledger")
    public ResponseEntity<ApiResponse<PageResponse<LedgerService.View>>> ledger(@AuthenticationPrincipal AuthUser me,
                                                                               @RequestParam(required = false) LedgerEntry.Type type,
                                                                               @RequestParam(required = false) LedgerEntry.Status status,
                                                                               @RequestParam(required = false) Integer page,
                                                                               @RequestParam(required = false) Integer size) {
        Provider p = access.own(me);
        return ApiResponse.ok(ledger.mine(me.id(), p.getId(), type, status, page, size));
    }

    @PostMapping("/ledger/{id}/report-paid")
    public ResponseEntity<ApiResponse<LedgerService.View>> reportPaid(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                      @Valid @RequestBody LedgerService.ReportPaidRequest body) {
        return ApiResponse.ok(ledger.reportPaid(me, id, body));
    }
}
