package com.doubleclick.wadii.payment;

import com.doubleclick.wadii.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;

/** Issued once when the order completes; every number is derived from payments, charges and refunds. */
@Entity
@Immutable
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
public class Invoice extends BaseEntity {

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "invoice_number", nullable = false, unique = true)
    private String invoiceNumber;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discount;
    @Column(name = "additional_charges", nullable = false, precision = 12, scale = 2)
    private BigDecimal additionalCharges;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal refunds;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal paid;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal remaining;
    @Column(name = "platform_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal platformFee;
    @Column(name = "provider_net", nullable = false, precision = 12, scale = 2)
    private BigDecimal providerNet;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;
}
