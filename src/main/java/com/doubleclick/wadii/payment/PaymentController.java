package com.doubleclick.wadii.payment;

import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.common.security.Permissions;
import com.doubleclick.wadii.payment.dto.PaymentDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final RefundService refundService;
    private final AdditionalChargeService chargeService;
    private final InvoiceService invoiceService;

    // ---------------------------------------------------------------- payments

    @GetMapping("/orders/{orderId}/financials")
    public ResponseEntity<ApiResponse<OrderFinancials>> financials(@AuthenticationPrincipal AuthUser me, @PathVariable Long orderId) {
        return ApiResponse.ok(paymentService.financials(me, orderId));
    }

    @GetMapping("/payments/{id}")
    public ResponseEntity<ApiResponse<PaymentView>> get(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(paymentService.get(me, id));
    }

    @GetMapping("/payments/mine")
    public ResponseEntity<ApiResponse<PageResponse<PaymentView>>> mine(@AuthenticationPrincipal AuthUser me,
                                                                       @RequestParam(required = false) Integer page,
                                                                       @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(paymentService.mine(me, page, size));
    }

    @PostMapping("/payments/{id}/method")
    public ResponseEntity<ApiResponse<PaymentView>> selectMethod(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                 @Valid @RequestBody SelectMethodRequest request) {
        return ApiResponse.ok(paymentService.selectMethod(me, id, request.method()), "Payment method selected");
    }

    @PostMapping(value = "/payments/{id}/proofs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PaymentView>> uploadProof(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                @RequestParam("file") MultipartFile file,
                                                                @RequestParam(required = false) String transactionReference,
                                                                @RequestParam(required = false) String senderIdentifier,
                                                                @RequestParam(required = false) String note) {
        return ApiResponse.ok(paymentService.uploadProof(me, id, file, transactionReference, senderIdentifier, note),
                "Proof submitted; waiting for the provider to verify the transfer");
    }

    @GetMapping("/payment-proofs/{proofId}/file")
    public ResponseEntity<Resource> proofFile(@AuthenticationPrincipal AuthUser me, @PathVariable Long proofId) {
        return paymentService.proofFile(me, proofId);
    }

    @PostMapping("/payments/{id}/confirm")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<PaymentView>> confirm(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(paymentService.confirm(me, id), "Payment confirmed");
    }

    @PostMapping("/payments/{id}/reject")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<PaymentView>> reject(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                           @Valid @RequestBody RejectRequest request) {
        return ApiResponse.ok(paymentService.reject(me, id, request.reason()), "Payment rejected");
    }

    // ---------------------------------------------------------------- refunds

    @PostMapping("/orders/{orderId}/refunds")
    public ResponseEntity<ApiResponse<RefundView>> requestRefund(@AuthenticationPrincipal AuthUser me, @PathVariable Long orderId,
                                                                 @Valid @RequestBody RefundRequest request) {
        return ApiResponse.created(refundService.request(me, orderId, request), "Refund requested");
    }

    @GetMapping("/refunds/{id}")
    public ResponseEntity<ApiResponse<RefundView>> refund(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(refundService.get(me, id));
    }

    @PostMapping("/refunds/{id}/decision")
    public ResponseEntity<ApiResponse<RefundView>> decideRefund(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                @Valid @RequestBody DecisionRequest request) {
        return ApiResponse.ok(refundService.decide(me, id, request.approve(), request.note()), "Refund decision saved");
    }

    @PostMapping("/refunds/{id}/complete")
    public ResponseEntity<ApiResponse<RefundView>> completeRefund(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(refundService.complete(me, id), "Refund marked as sent");
    }

    // ---------------------------------------------------------------- additional charges

    @GetMapping("/orders/{orderId}/charges")
    public ResponseEntity<ApiResponse<List<ChargeView>>> charges(@AuthenticationPrincipal AuthUser me, @PathVariable Long orderId) {
        return ApiResponse.ok(chargeService.list(me, orderId));
    }

    @PostMapping("/orders/{orderId}/charges")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<ChargeView>> requestCharge(@AuthenticationPrincipal AuthUser me, @PathVariable Long orderId,
                                                                 @Valid @RequestBody ChargeRequest request) {
        return ApiResponse.created(chargeService.request(me, orderId, request), "Additional charge requested");
    }

    @PostMapping("/charges/{id}/decision")
    public ResponseEntity<ApiResponse<ChargeView>> decideCharge(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                @Valid @RequestBody DecisionRequest request) {
        return ApiResponse.ok(chargeService.decide(me, id, request.approve()), "Decision saved");
    }

    @PostMapping("/charges/{id}/cancel")
    @PreAuthorize(Permissions.PROVIDER)
    public ResponseEntity<ApiResponse<ChargeView>> cancelCharge(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(chargeService.cancel(me, id), "Charge withdrawn");
    }

    // ---------------------------------------------------------------- invoice

    @GetMapping("/orders/{orderId}/invoice")
    public ResponseEntity<ApiResponse<InvoiceView>> invoice(@AuthenticationPrincipal AuthUser me, @PathVariable Long orderId) {
        return ApiResponse.ok(invoiceService.get(me, orderId));
    }

    // ---------------------------------------------------------------- finance admin

    @GetMapping("/admin/payments")
    @PreAuthorize(Permissions.FINANCE_ADMIN)
    public ResponseEntity<ApiResponse<PageResponse<PaymentView>>> adminPayments(@RequestParam(required = false) PaymentStatus status,
                                                                                @RequestParam(required = false) Integer page,
                                                                                @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(paymentService.adminList(status, page, size));
    }

    @GetMapping("/admin/refunds")
    @PreAuthorize(Permissions.FINANCE_ADMIN)
    public ResponseEntity<ApiResponse<PageResponse<RefundView>>> adminRefunds(@RequestParam(required = false) RefundStatus status,
                                                                              @RequestParam(required = false) Integer page,
                                                                              @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(refundService.adminList(status, page, size));
    }
}
