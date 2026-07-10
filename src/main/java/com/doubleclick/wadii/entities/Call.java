package com.doubleclick.wadii.entities;

import com.doubleclick.wadii.auth.model.User;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "calls")
@AllArgsConstructor
@NoArgsConstructor
public class Call {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caller_id", nullable = false)
    @JsonIgnoreProperties({"sentMessages", "receivedMessages", "orders", "rates",
            "following", "provider", "password", "token",
            "hibernateLazyInitializer", "handler"})
    private User caller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "callee_id", nullable = false)
    @JsonIgnoreProperties({"sentMessages", "receivedMessages", "orders", "rates",
            "following", "provider", "password", "token",
            "hibernateLazyInitializer", "handler"})
    private User callee;

    @Enumerated(EnumType.STRING)
    @Column(name = "call_type", nullable = false)
    private CallType callType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CallStatus status = CallStatus.RINGING;

    // room/channel name, built client-side as sorted(userIdA, userIdB).join("_"), e.g. "3_7"
    @Column(name = "room_name", nullable = false)
    private String roomName;

    // when the call was initiated (CALL_INVITE)
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    // when the call actually ended (CALL_END/CALL_REJECT), null while still ringing/ongoing
    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    // length of the call in seconds, set once it ends; 0/null if never connected
    @Column(name = "duration_seconds")
    private Long durationSeconds;
}
