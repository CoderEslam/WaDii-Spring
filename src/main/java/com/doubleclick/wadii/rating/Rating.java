package com.doubleclick.wadii.rating;

import com.doubleclick.wadii.common.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One rating per completed order, by its customer, for the accepted provider. */
@Entity
@Table(name = "ratings")
@Getter
@Setter
@NoArgsConstructor
public class Rating extends AuditedEntity {

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "provider_id", nullable = false, updatable = false)
    private Long providerId;

    @Column(nullable = false)
    private int score;

    private String comment;
}
