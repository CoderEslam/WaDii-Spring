package com.doubleclick.wadii.payment;

import com.doubleclick.wadii.common.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A request to pay an amount due on an order. The amount is set by the server from the financial records,
 * never by the client. A changed method after a proof was submitted creates a new payment; the old one is CANCELED.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment extends AuditedEntity {

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "provider_id", nullable = false, updatable = false)
    private Long providerId;

    @Enumerated(EnumType.STRING)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "amount_paid", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(name = "amount_refunded", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountRefunded = BigDecimal.ZERO;

    /** Receiving account copied when the method was selected, so later edits by the provider do not change it. */
    @Column(name = "payee_account_name")
    private String payeeAccountName;

    @Column(name = "payee_account_identifier")
    private String payeeAccountIdentifier;

    private Instant methodSelectedAt;
    private Instant submittedAt;
    private Instant confirmedAt;
    @Column(name = "confirmed_by")
    private Long confirmedBy;
    private Instant rejectedAt;
    private String rejectionReason;
    private Instant disputedAt;
    private Instant closedAt;
    private String metadata;

    @Version
    private long version;

    public BigDecimal remainingAmount() {
        return amount.subtract(amountPaid).max(BigDecimal.ZERO);
    }

    public BigDecimal refundableAmount() {
        return amountPaid.subtract(amountRefunded).max(BigDecimal.ZERO);
    }
}
