package com.doubleclick.wadii.order;

import com.doubleclick.wadii.catalog.CancellationReason;
import com.doubleclick.wadii.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "order_cancellations")
@Getter
@Setter
@NoArgsConstructor
public class OrderCancellation extends BaseEntity {

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    /** Null when the system canceled the order. */
    @Column(name = "canceled_by")
    private Long canceledBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", nullable = false)
    private ActorRole actorRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reason_id")
    private CancellationReason reason;

    @Column(name = "reason_text")
    private String reasonText;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status_at_cancel", nullable = false)
    private OrderStatus orderStatusAtCancel;
}
