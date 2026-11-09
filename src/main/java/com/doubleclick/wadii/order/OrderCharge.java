package com.doubleclick.wadii.order;

import com.doubleclick.wadii.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** Additional work proposed during the service; it changes the amount due only after the customer approves it. */
@Getter
@Setter
@Entity
@Table(name = "order_charges")
@NoArgsConstructor
public class OrderCharge extends BaseEntity {

    public enum Status { PENDING, APPROVED, REJECTED }

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "decided_at")
    private Instant decidedAt;
}
