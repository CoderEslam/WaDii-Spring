package com.doubleclick.wadii.order;

import com.doubleclick.wadii.common.persistence.AuditedEntity;
import com.doubleclick.wadii.offer.OfferLine;
import com.doubleclick.wadii.settings.Configs;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The accepted assignment of one service item to one provider. Service execution ({@link Status}) and cash
 * collection ({@link CashStatus}) are independent state machines: neither is ever inferred from the other.
 * Prices, commission, warranty and policy terms are snapshots taken at acceptance and never change afterwards;
 * later extra work goes through {@link OrderCharge} approval.
 */
@Getter
@Setter
@Entity
@Table(name = "service_orders")
public class ServiceOrder extends AuditedEntity {

    public enum Status {
        ACCEPTED, IN_PROGRESS, PROVIDER_MARKED_COMPLETED, COMPLETED, INTERRUPTION_REQUESTED,
        INTERRUPTION_STATEMENT_SUBMITTED, INTERRUPTED, DISPUTED, CANCELED
    }

    public enum CashStatus { NOT_DUE, DUE, REPORTED_COLLECTED, CONFIRMED, PARTIAL, DISPUTED }

    public enum CancellationKind { CUSTOMER_CANCELED, PROVIDER_CANCELED, CUSTOMER_NO_SHOW, PROVIDER_NO_SHOW, ADMIN_CANCELED }

    public enum InterruptionOutcome { ACCEPTED, DISPUTED, RESOLVED }

    @Column(name = "request_id", nullable = false, updatable = false)
    private Long requestId;

    @Column(name = "service_item_id", nullable = false, updatable = false)
    private Long serviceItemId;

    @Column(name = "line_id", nullable = false, updatable = false)
    private Long lineId;

    @Column(name = "offer_id", nullable = false, updatable = false)
    private Long offerId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "provider_id", nullable = false, updatable = false)
    private Long providerId;

    @Column(name = "provider_user_id", nullable = false, updatable = false)
    private Long providerUserId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @Column(name = "category_id", nullable = false, updatable = false)
    private Long categoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACCEPTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "cash_status", nullable = false)
    private CashStatus cashStatus = CashStatus.NOT_DUE;

    @Column(name = "service_price", nullable = false, updatable = false)
    private BigDecimal servicePrice;

    @Column(name = "labor_cost", nullable = false, updatable = false)
    private BigDecimal laborCost;

    @Column(name = "parts_total", nullable = false, updatable = false)
    private BigDecimal partsTotal;

    @Column(nullable = false, updatable = false)
    private BigDecimal discount;

    @Column(name = "provider_total", nullable = false, updatable = false)
    private BigDecimal providerTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "commission_mode", nullable = false, updatable = false)
    private Configs.FeeMode commissionMode;

    @Column(name = "commission_value", nullable = false, updatable = false)
    private BigDecimal commissionValue;

    @Column(name = "commission_base", nullable = false, updatable = false)
    private BigDecimal commissionBase;

    @Column(name = "commission_amount", nullable = false, updatable = false)
    private BigDecimal commissionAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "commission_payer", nullable = false, updatable = false)
    private Configs.CommissionPayer commissionPayer;

    @Column(name = "commission_config_id", updatable = false)
    private Long commissionConfigId;

    @Column(name = "customer_total", nullable = false, updatable = false)
    private BigDecimal customerTotal;

    @Column(name = "approved_charges_total", nullable = false)
    private BigDecimal approvedChargesTotal = BigDecimal.ZERO;

    /** Set when the amount due is decided by an interruption statement or a dispute instead of the agreement. */
    @Column(name = "final_amount")
    private BigDecimal finalAmount;

    @Column(name = "cash_reported_amount")
    private BigDecimal cashReportedAmount;

    @Column(name = "cash_reported_at")
    private Instant cashReportedAt;

    @Column(name = "cash_confirmed_at")
    private Instant cashConfirmedAt;

    @Column(name = "warranty_provided", nullable = false, updatable = false)
    private boolean warrantyProvided;

    @Column(name = "warranty_days", updatable = false)
    private Integer warrantyDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "warranty_coverage", updatable = false)
    private OfferLine.WarrantyCoverage warrantyCoverage;

    @Column(name = "warranty_exclusions", updatable = false)
    private String warrantyExclusions;

    @Column(name = "warranty_conditions", updatable = false)
    private String warrantyConditions;

    @Column(name = "policy_snapshot", nullable = false, updatable = false)
    private String policySnapshot;

    @Column(name = "appointment_start")
    private Instant appointmentStart;

    @Column(name = "appointment_end")
    private Instant appointmentEnd;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "provider_completed_at")
    private Instant providerCompletedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_kind")
    private CancellationKind cancellationKind;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "canceled_by")
    private Long canceledBy;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Column(name = "interruption_requested_at")
    private Instant interruptionRequestedAt;

    @Column(name = "interruption_reason")
    private String interruptionReason;

    @Column(name = "interruption_work_done")
    private String interruptionWorkDone;

    @Column(name = "interruption_labor")
    private BigDecimal interruptionLabor;

    @Column(name = "interruption_parts")
    private BigDecimal interruptionParts;

    @Column(name = "interruption_proposed")
    private BigDecimal interruptionProposed;

    @Column(name = "interruption_evidence")
    private String interruptionEvidence;

    @Column(name = "interruption_statement_at")
    private Instant interruptionStatementAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "interruption_outcome")
    private InterruptionOutcome interruptionOutcome;

    @Version
    private long version;

    /** What the customer owes the provider in cash right now. */
    public BigDecimal amountDue() {
        if (finalAmount != null) {
            return finalAmount;
        }
        return customerTotal.add(approvedChargesTotal);
    }

    public boolean isFinished() {
        return status == Status.COMPLETED || status == Status.CANCELED || status == Status.INTERRUPTED;
    }
}
