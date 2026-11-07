package com.doubleclick.wadii.offer;

import com.doubleclick.wadii.common.persistence.AuditedEntity;
import com.doubleclick.wadii.settings.Configs;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The provider's price and terms for one service item. Prices are set by the provider; the server computes the
 * totals and the WaDii commission (with the configuration version in force when the line was priced), so the
 * customer accepts exactly the breakdown they saw. Warranty is stated per line, including an explicit "no warranty".
 */
@Getter
@Setter
@Entity
@Table(name = "offer_lines")
public class OfferLine extends AuditedEntity {

    public enum Status { PENDING, UNAVAILABLE, ACCEPTED, DECLINED, WITHDRAWN, EXPIRED }

    public enum WarrantyCoverage { PARTS, LABOR, PARTS_AND_LABOR }

    @Column(name = "offer_id", nullable = false, updatable = false)
    private Long offerId;

    @Column(name = "service_item_id", nullable = false, updatable = false)
    private Long serviceItemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private boolean available;

    @Column(name = "service_price", nullable = false)
    private BigDecimal servicePrice;

    @Column(name = "labor_cost", nullable = false)
    private BigDecimal laborCost;

    @Column(name = "parts_total", nullable = false)
    private BigDecimal partsTotal;

    @Column(nullable = false)
    private BigDecimal discount;

    @Column(name = "provider_total", nullable = false)
    private BigDecimal providerTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "commission_mode", nullable = false)
    private Configs.FeeMode commissionMode;

    @Column(name = "commission_value", nullable = false)
    private BigDecimal commissionValue;

    @Column(name = "commission_amount", nullable = false)
    private BigDecimal commissionAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "commission_payer", nullable = false)
    private Configs.CommissionPayer commissionPayer;

    @Column(name = "commission_config_id")
    private Long commissionConfigId;

    @Column(name = "customer_total", nullable = false)
    private BigDecimal customerTotal;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    @Column(name = "proposed_start")
    private Instant proposedStart;

    private String notes;

    @Column(name = "warranty_provided", nullable = false)
    private boolean warrantyProvided;

    @Column(name = "warranty_days")
    private Integer warrantyDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "warranty_coverage")
    private WarrantyCoverage warrantyCoverage;

    @Column(name = "warranty_exclusions")
    private String warrantyExclusions;

    @Column(name = "warranty_conditions")
    private String warrantyConditions;

    @Column(name = "order_id")
    private Long orderId;
}
