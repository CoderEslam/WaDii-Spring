package com.doubleclick.wadii.listing;

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
 * A used item listed by an individual seller. Separate from the service-order lifecycle: selling never makes an
 * account a provider. Lifecycle: DRAFT -> PENDING_REVIEW -> ACTIVE -> SOLD | CLOSED | REJECTED | EXPIRED.
 */
@Getter
@Setter
@Entity
@Table(name = "listings")
public class Listing extends AuditedEntity {

    public enum Status { DRAFT, PENDING_REVIEW, ACTIVE, SOLD, CLOSED, REJECTED, EXPIRED }

    public enum ContactMethod { CHAT, PHONE }

    @Column(name = "seller_id", nullable = false, updatable = false)
    private Long sellerId;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListingCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_condition", nullable = false)
    private ListingCondition condition;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(name = "area_id", nullable = false)
    private Long areaId;

    @Column(name = "location_text")
    private String locationText;

    @Enumerated(EnumType.STRING)
    @Column(name = "contact_method", nullable = false)
    private ContactMethod contactMethod;

    @Column(name = "photo_keys")
    private String photoKeys;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "sold_at")
    private Instant soldAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Version
    private long version;
}
