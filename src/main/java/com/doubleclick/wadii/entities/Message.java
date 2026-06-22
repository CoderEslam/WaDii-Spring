package com.doubleclick.wadii.entities;

import com.doubleclick.wadii.auth.model.User;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Setter
@Getter
@Table(name = "messages")
@AllArgsConstructor
@NoArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String text;

    // "text", "image", "audio", etc. Keep it a string so you can extend.
    private String type;

    private LocalDateTime createdAt = LocalDateTime.now();

    @JsonProperty("isRead")
    @Column(name = "is_read", nullable = true)
    private boolean isRead = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_id", nullable = false)
    @JsonIgnoreProperties({"sentMessages", "receivedMessages", "orders", "rates",
            "following", "provider", "password", "token",
            "hibernateLazyInitializer", "handler"})
    private User fromUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_id", nullable = false)
    @JsonIgnoreProperties({"sentMessages", "receivedMessages", "orders", "rates",
            "following", "provider", "password", "token",
            "hibernateLazyInitializer", "handler"})
    private User toUser;
}
