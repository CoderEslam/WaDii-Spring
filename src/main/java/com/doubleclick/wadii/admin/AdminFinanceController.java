package com.doubleclick.wadii.admin;

import com.doubleclick.wadii.audit.AuditLog;
import com.doubleclick.wadii.audit.AuditLogRepository;
import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.common.security.Permissions;
import com.doubleclick.wadii.ledger.LedgerEntry;
import com.doubleclick.wadii.ledger.LedgerService;
import com.doubleclick.wadii.report.AdminReportService;
import com.doubleclick.wadii.subscription.SubscriptionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Admin: subscriptions, ledger, reports and the audit trail. Financial changes need the finance role. */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminFinanceController {

    private final SubscriptionService subscriptions;
    private final LedgerService ledger;
    private final AdminReportService reports;
    private final AuditLogRepository auditLogs;

    public record WaiveInput(@NotBlank @Size(max = 1000) String note) {
    }

    public record AdjustmentInput(@NotNull Long payerUserId, Long providerId,
                                  @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
                                  @NotBlank @Size(max = 1000) String note) {
    }

    public record AuditView(Long id, Long actorId, String action, String entityType, Long entityId, String details, String ip,
                            Instant createdAt) {
        static AuditView of(AuditLog a) {
            return new AuditView(a.getId(), a.getActorId(), a.getAction(), a.getEntityType(), a.getEntityId(), a.getDetails(),
                    a.getIp(), a.getCreatedAt());
        }
    }

    @GetMapping("/subscriptions")
    @PreAuthorize(Permissions.FINANCE_ADMIN)
    public ResponseEntity<ApiResponse<PageResponse<SubscriptionService.AdminRow>>> subscriptions(
            @RequestParam(required = false) SubscriptionService.Status status,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(subscriptions.adminList(status, page, size));
    }

    @PostMapping("/subscriptions/{providerId}/payments")
    @PreAuthorize(Permissions.FINANCE_ADMIN)
    public ResponseEntity<ApiResponse<SubscriptionService.View>> payment(@AuthenticationPrincipal AuthUser me, @PathVariable Long providerId,
                                                                         @Valid @RequestBody SubscriptionService.PaymentRequest body) {
        return ApiResponse.created(subscriptions.recordPayment(me, providerId, body), "Payment recorded");
    }

    @PostMapping("/subscriptions/{providerId}/exemptions")
    @PreAuthorize(Permissions.FINANCE_ADMIN)
    public ResponseEntity<ApiResponse<SubscriptionService.View>> exemption(@AuthenticationPrincipal AuthUser me, @PathVariable Long providerId,
                                                                           @Valid @RequestBody SubscriptionService.ExemptionRequest body) {
        return ApiResponse.created(subscriptions.exempt(me, providerId, body), "Exemption granted");
    }

    @GetMapping("/ledger")
    @PreAuthorize(Permissions.FINANCE_ADMIN)
    public ResponseEntity<ApiResponse<PageResponse<LedgerService.View>>> ledger(
            @RequestParam(required = false) LedgerEntry.Type type, @RequestParam(required = false) LedgerEntry.Status status,
            @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(ledger.mine(null, providerId, type, status, page, size));
    }

    @PostMapping("/ledger/{id}/settle")
    @PreAuthorize(Permissions.FINANCE_ADMIN)
    public ResponseEntity<ApiResponse<LedgerService.View>> settle(@PathVariable Long id, @Valid @RequestBody LedgerService.SettleRequest body) {
        return ApiResponse.ok(ledger.settle(id, body));
    }

    @PostMapping("/ledger/{id}/waive")
    @PreAuthorize(Permissions.FINANCE_ADMIN)
    public ResponseEntity<ApiResponse<LedgerService.View>> waive(@PathVariable Long id, @Valid @RequestBody WaiveInput body) {
        return ApiResponse.ok(ledger.waive(id, body.note()));
    }

    @PostMapping("/ledger/adjustments")
    @PreAuthorize(Permissions.FINANCE_ADMIN)
    public ResponseEntity<ApiResponse<LedgerService.View>> adjustment(@AuthenticationPrincipal AuthUser me,
                                                                      @Valid @RequestBody AdjustmentInput body) {
        return ApiResponse.created(ledger.adjustment(me, body.payerUserId(), body.providerId(), body.amount(), body.note()), "Adjustment recorded");
    }

    @GetMapping("/reports/summary")
    @PreAuthorize(Permissions.ADMIN)
    public ResponseEntity<ApiResponse<AdminReportService.Summary>> summary(@RequestParam(required = false) LocalDate from,
                                                                           @RequestParam(required = false) LocalDate to) {
        return ApiResponse.ok(reports.summary(from, to));
    }

    @GetMapping("/audit")
    @PreAuthorize(Permissions.SUPER_ADMIN)
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PageResponse<AuditView>>> audit(@RequestParam(required = false) String entityType,
                                                                      @RequestParam(required = false) Long entityId,
                                                                      @RequestParam(required = false) Integer page,
                                                                      @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(PageResponse.of(auditLogs.search(entityType, entityId,
                PageResponse.pageable(page, size, org.springframework.data.domain.Sort.by("id").descending())), AuditView::of));
    }
}
