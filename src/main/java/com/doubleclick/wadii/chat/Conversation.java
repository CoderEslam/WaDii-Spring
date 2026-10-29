package com.doubleclick.wadii.chat;

import com.doubleclick.wadii.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Chat of one order, between its customer and its accepted provider. Created when an offer is accepted. */
@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
public class Conversation extends BaseEntity {

    @Column(name = "order_id", nullable = false, unique = true, updatable = false)
    private Long orderId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "provider_id", nullable = false, updatable = false)
    private Long providerId;

    @Column(name = "provider_user_id", nullable = false, updatable = false)
    private Long providerUserId;

    private Instant lastMessageAt;

    private String lastMessagePreview;

    public boolean isParticipant(Long userId) {
        return customerId.equals(userId) || providerUserId.equals(userId);
    }

    public Long otherParticipant(Long userId) {
        return customerId.equals(userId) ? providerUserId : customerId;
    }
}
