package com.doubleclick.wadii.dispute;

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

/**
 * A disagreement for staff to decide: service quality, interruption amount, cash collection, warranty,
 * cancellation, or a used-item listing. The outcome is applied to the related record when it is resolved.
 */
@Getter
@Setter
@Entity
@Table(name = "disputes")
public class Dispute extends AuditedEntity {

    public enum Type { SERVICE_QUALITY, INTERRUPTION, CASH_COLLECTION, WARRANTY, CANCELLATION, LISTING, OTHER }

    public enum Status { OPEN, UNDER_REVIEW, RESOLVED, REJECTED }

    public enum PartyRole { CUSTOMER, PROVIDER, SELLER, BUYER }

    public enum Outcome {
        COMPLETE_ORDER, REOPEN_ORDER, CANCEL_ORDER, SET_FINAL_AMOUNT, CASH_CONFIRMED, CASH_NOT_COLLECTED,
        WARRANTY_HONORED, NO_ACTION
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "dispute_type", nullable = false, updatable = false)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "listing_id")
    private Long listingId;

    @Column(name = "warranty_claim_id")
    private Long warrantyClaimId;

    @Column(name = "opened_by", nullable = false, updatable = false)
    private Long openedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "opened_by_role", nullable = false, updatable = false)
    private PartyRole openedByRole;

    @Column(name = "respondent_user_id")
    private Long respondentUserId;

    @Column(nullable = false)
    private String description;

    @Column(name = "assigned_admin_id")
    private Long assignedAdminId;

    @Enumerated(EnumType.STRING)
    private Outcome outcome;

    @Column(name = "resolution_note")
    private String resolutionNote;

    @Column(name = "final_amount")
    private BigDecimal finalAmount;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Version
    private long version;

    public boolean isOpen() {
        return status == Status.OPEN || status == Status.UNDER_REVIEW;
    }

    public boolean involves(Long userId) {
        return openedBy.equals(userId) || userId.equals(respondentUserId);
    }
}
