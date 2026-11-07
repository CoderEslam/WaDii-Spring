package com.doubleclick.wadii.chat;

import com.doubleclick.wadii.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Chat messages are kept (never edited) because they may become dispute evidence. */
@Getter
@Entity
@Table(name = "messages")
@NoArgsConstructor
public class Message extends BaseEntity {

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    private String body;

    @Column(name = "file_key")
    private String fileKey;

    public Message(Long conversationId, Long senderId, String body, String fileKey) {
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.body = body;
        this.fileKey = fileKey;
    }
}
