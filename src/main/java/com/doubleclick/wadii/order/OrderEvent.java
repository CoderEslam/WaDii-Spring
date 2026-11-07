package com.doubleclick.wadii.order;

import com.doubleclick.wadii.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Append-only timeline of a Service Order, shown to both parties and to support. */
@Getter
@Entity
@Table(name = "order_events")
@NoArgsConstructor
public class OrderEvent extends BaseEntity {

    public enum ActorRole { CUSTOMER, PROVIDER, ADMIN, SYSTEM }

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "event_type", nullable = false)
    private String type;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", nullable = false)
    private ActorRole actorRole;

    private String note;

    public OrderEvent(Long orderId, String type, Long actorUserId, ActorRole actorRole, String note) {
        this.orderId = orderId;
        this.type = type;
        this.actorUserId = actorUserId;
        this.actorRole = actorRole;
        this.note = note;
    }
}
