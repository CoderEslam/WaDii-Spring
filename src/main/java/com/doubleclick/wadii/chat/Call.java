package com.doubleclick.wadii.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "calls")
@Getter
@Setter
@NoArgsConstructor
public class Call {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "caller_id", nullable = false)
    private Long callerId;

    @Column(name = "callee_id", nullable = false)
    private Long calleeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "call_type", nullable = false)
    private CallType callType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CallStatus status = CallStatus.RINGING;

    /** Server-generated: {@code conv_<conversationId>}. */
    @Column(name = "channel_name", nullable = false)
    private String channelName;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    private Instant answeredAt;
    private Instant endedAt;
    private Long durationSeconds;
}
