package com.doubleclick.wadii.ledger;

import com.doubleclick.wadii.common.persistence.AuditedEntity;
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
import java.time.LocalDate;

/**
 * An amount someone owes WaDii. Each revenue stream has its own type so they are never merged into one generic
 * fee. In the cash-only V1 nothing is collected automatically: an entry is DUE until the payer reports paying and
 * finance confirms it as SETTLED (or waives it).
 */
@Getter
@Setter
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry extends AuditedEntity {

    public enum Type { SUBSCRIPTION, COMMISSION, SELLER_FEE, CANCELLATION_FEE, ADJUSTMENT }

    public enum Status { DUE, REPORTED_PAID, SETTLED, WAIVED, CANCELED }

    public enum Method { CASH, BANK_TRANSFER, WALLET }

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, updatable = false)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DUE;

    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false)
    private String currency = "EGP";

    @Column(name = "payer_user_id", nullable = false, updatable = false)
    private Long payerUserId;

    @Column(name = "provider_id")
    private Long providerId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "listing_id")
    private Long listingId;

    @Column(name = "rule_description", nullable = false)
    private String ruleDescription;

    @Column(name = "config_version_id")
    private Long configVersionId;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "reported_at")
    private Instant reportedAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Enumerated(EnumType.STRING)
    private Method method;

    private String reference;

    private String note;

    @Column(name = "created_by")
    private Long createdBy;

    @Version
    private long version;

    public boolean isOpen() {
        return status == Status.DUE || status == Status.REPORTED_PAID;
    }
}
