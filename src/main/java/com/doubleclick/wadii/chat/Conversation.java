package com.doubleclick.wadii.chat;

import com.doubleclick.wadii.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A two-party thread attached to an order, a request (customer and an invited provider) or a listing. Participants
 * are stored with the smaller user id first so each pair has exactly one thread per subject.
 */
@Getter
@Setter
@Entity
@Table(name = "conversations")
@NoArgsConstructor
public class Conversation extends BaseEntity {

    public enum Kind { ORDER, REQUEST, LISTING }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Kind kind;

    @Column(name = "ref_id", nullable = false, updatable = false)
    private Long refId;

    @Column(name = "user_a", nullable = false, updatable = false)
    private Long userA;

    @Column(name = "user_b", nullable = false, updatable = false)
    private Long userB;

    @Column(name = "a_last_read_id", nullable = false)
    private long aLastReadId;

    @Column(name = "b_last_read_id", nullable = false)
    private long bLastReadId;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    public boolean hasParticipant(Long userId) {
        return userA.equals(userId) || userB.equals(userId);
    }

    public Long other(Long userId) {
        return userA.equals(userId) ? userB : userA;
    }

    public long lastReadBy(Long userId) {
        return userA.equals(userId) ? aLastReadId : bLastReadId;
    }

    public void markRead(Long userId, long messageId) {
        if (userA.equals(userId)) aLastReadId = Math.max(aLastReadId, messageId);
        else if (userB.equals(userId)) bLastReadId = Math.max(bLastReadId, messageId);
    }
}
