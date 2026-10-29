package com.doubleclick.wadii.payment.dto;

import com.doubleclick.wadii.payment.AdditionalCharge;
import com.doubleclick.wadii.payment.ChargeStatus;
import com.doubleclick.wadii.payment.Invoice;
import com.doubleclick.wadii.payment.Payment;
import com.doubleclick.wadii.payment.PaymentMethod;
import com.doubleclick.wadii.payment.PaymentProof;
import com.doubleclick.wadii.payment.PaymentStatus;
import com.doubleclick.wadii.payment.ProofStatus;
import com.doubleclick.wadii.payment.Refund;
import com.doubleclick.wadii.payment.RefundSource;
import com.doubleclick.wadii.payment.RefundStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record PayeeView(String accountName, String accountIdentifier) {
    }

    public record ProofView(Long id, String contentType, String transactionReference, String senderIdentifier, String note,
                            Long uploadedBy, Instant uploadedAt, ProofStatus status, Instant reviewedAt, String rejectionReason) {
        public static ProofView of(PaymentProof p) {
            return new ProofView(p.getId(), p.getContentType(), p.getTransactionReference(), p.getSenderIdentifier(), p.getNote(),
                    p.getUploadedBy(), p.getUploadedAt(), p.getStatus(), p.getReviewedAt(), p.getRejectionReason());
        }
    }

    public record PaymentView(Long id, Long orderId, PaymentMethod method, PaymentStatus status, BigDecimal amount, String currency,
                              BigDecimal amountPaid, BigDecimal amountRefunded, BigDecimal remainingAmount, PayeeView payee,
                              Instant createdAt, Instant methodSelectedAt, Instant submittedAt, Instant confirmedAt,
                              Long confirmedBy, Instant rejectedAt, String rejectionReason, Instant disputedAt, Instant closedAt,
                              List<ProofView> proofs) {
        public static PaymentView of(Payment p, List<PaymentProof> proofs) {
            PayeeView payee = p.getPayeeAccountIdentifier() == null ? null : new PayeeView(p.getPayeeAccountName(), p.getPayeeAccountIdentifier());
            return new PaymentView(p.getId(), p.getOrderId(), p.getMethod(), p.getStatus(), p.getAmount(), p.getCurrency(),
                    p.getAmountPaid(), p.getAmountRefunded(), p.remainingAmount(), payee, p.getCreatedAt(), p.getMethodSelectedAt(),
                    p.getSubmittedAt(), p.getConfirmedAt(), p.getConfirmedBy(), p.getRejectedAt(), p.getRejectionReason(),
                    p.getDisputedAt(), p.getClosedAt(), proofs.stream().map(ProofView::of).toList());
        }
    }

    public record SelectMethodRequest(@NotNull PaymentMethod method) {
    }

    public record RejectRequest(@NotBlank @Size(max = 1000) String reason) {
    }

    public record RefundView(Long id, Long paymentId, Long orderId, BigDecimal amount, String currency, String reason,
                             RefundSource source, RefundStatus status, Long disputeId, Long requestedBy, Long approvedBy,
                             String decisionNote, Instant createdAt, Instant approvedAt, Instant processedAt) {
        public static RefundView of(Refund r) {
            return new RefundView(r.getId(), r.getPaymentId(), r.getOrderId(), r.getAmount(), r.getCurrency(), r.getReason(),
                    r.getSource(), r.getStatus(), r.getDisputeId(), r.getRequestedBy(), r.getApprovedBy(), r.getDecisionNote(),
                    r.getCreatedAt(), r.getApprovedAt(), r.getProcessedAt());
        }
    }

    public record RefundRequest(Long paymentId,
                                @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
                                @NotBlank @Size(max = 1000) String reason) {
    }

    public record DecisionRequest(@NotNull Boolean approve, @Size(max = 1000) String note) {
    }

    public record ChargeView(Long id, Long orderId, BigDecimal amount, String currency, String reason, ChargeStatus status,
                             Long requestedBy, Long decidedBy, Instant decidedAt, Instant createdAt) {
        public static ChargeView of(AdditionalCharge c) {
            return new ChargeView(c.getId(), c.getOrderId(), c.getAmount(), c.getCurrency(), c.getReason(), c.getStatus(),
                    c.getRequestedBy(), c.getDecidedBy(), c.getDecidedAt(), c.getCreatedAt());
        }
    }

    public record ChargeRequest(@NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
                                @NotBlank @Size(max = 1000) String reason) {
    }

    public record InvoiceView(Long id, String invoiceNumber, Long orderId, Long providerId, Long customerId, BigDecimal subtotal,
                              BigDecimal discount, BigDecimal additionalCharges, BigDecimal refunds, BigDecimal total,
                              BigDecimal paid, BigDecimal remaining, BigDecimal platformFee, BigDecimal providerNet,
                              String currency, Instant issuedAt) {
        public static InvoiceView of(Invoice i) {
            return new InvoiceView(i.getId(), i.getInvoiceNumber(), i.getOrderId(), i.getProviderId(), i.getCustomerId(),
                    i.getSubtotal(), i.getDiscount(), i.getAdditionalCharges(), i.getRefunds(), i.getTotal(), i.getPaid(),
                    i.getRemaining(), i.getPlatformFee(), i.getProviderNet(), i.getCurrency(), i.getIssuedAt());
        }
    }

    /** Everything money-related on one order, computed from the financial records. */
    public record OrderFinancials(Long orderId, String currency, BigDecimal acceptedPrice, BigDecimal approvedCharges,
                                  BigDecimal amountDue, BigDecimal paid, BigDecimal refunded, BigDecimal outstanding,
                                  List<PaymentView> payments, List<ChargeView> charges, List<RefundView> refunds) {
    }
}
