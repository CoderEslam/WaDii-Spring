package com.doubleclick.wadii.order;

import com.doubleclick.wadii.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A customer's rating of a completed Service Order. Content admins may hide it; it is never deleted. */
@Getter
@Setter
@Entity
@Table(name = "ratings")
@NoArgsConstructor
public class Rating extends BaseEntity {

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "provider_id", nullable = false, updatable = false)
    private Long providerId;

    @Column(nullable = false)
    private int stars;

    private String comment;

    @Column(nullable = false)
    private boolean hidden;
}
