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

@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
public class Message extends BaseEntity {

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private Long conversationId;

    /** Null for SYSTEM messages. */
    @Column(name = "sender_id", updatable = false)
    private Long senderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private MessageType type;

    @Column(updatable = false)
    private String text;

    @Column(name = "file_key", updatable = false)
    private String fileKey;

    @Column(name = "content_type", updatable = false)
    private String contentType;

    private Instant readAt;
}
