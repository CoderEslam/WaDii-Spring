package com.doubleclick.wadii.request;

import com.doubleclick.wadii.common.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/** One requested service inside a parent request. Accepting an offer line for it creates its own Service Order. */
@Getter
@Setter
@Entity
@Table(name = "service_items")
public class ServiceItem extends AuditedEntity {

    public enum Status { MATCHING, OFFERS_AVAILABLE, ACCEPTED, CANCELED, EXPIRED }

    @Column(name = "request_id", nullable = false, updatable = false)
    private Long requestId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.MATCHING;

    @Column(name = "order_id")
    private Long orderId;

    @Version
    private long version;

    /** Still waiting for (or comparing) offers. */
    public boolean isOpen() {
        return status == Status.MATCHING || status == Status.OFFERS_AVAILABLE;
    }
}
