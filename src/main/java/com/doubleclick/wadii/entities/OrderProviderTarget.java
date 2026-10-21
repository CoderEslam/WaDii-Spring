package com.doubleclick.wadii.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Records which providers were invited to respond to an order. */
@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "order_provider_targets",
        uniqueConstraints = @UniqueConstraint(columnNames = {"order_id", "provider_id"}))
public class OrderProviderTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    @JsonIgnore
    private Provider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TargetStatus status = TargetStatus.TARGETED;

    private LocalDateTime notifiedAt;

    private LocalDateTime viewedAt;

    private LocalDateTime respondedAt;
}
