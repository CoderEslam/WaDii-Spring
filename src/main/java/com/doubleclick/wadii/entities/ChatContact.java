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
@Table(
        name = "chat_contacts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "contact_id"})
)
@AllArgsConstructor
@NoArgsConstructor
public class ChatContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"sentMessages", "receivedMessages", "orders", "rates",
            "following", "provider", "password", "token",
            "hibernateLazyInitializer", "handler"})
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", nullable = false)
    @JsonIgnoreProperties({"sentMessages", "receivedMessages", "orders", "rates",
            "following", "provider", "password", "token",
            "hibernateLazyInitializer", "handler"})
    private User contact;

    private LocalDateTime lastMessageAt = LocalDateTime.now();

    private String lastMessage;
    private String messageType;
}