package com.doubleclick.wadii.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Setter
@Getter
@Table(name = "responses", uniqueConstraints = @UniqueConstraint(columnNames = {"order_id", "provider_id"}))
@AllArgsConstructor
@NoArgsConstructor
public class Responses {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String comment;
    private Double latitude;
    private Double longitude;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "responses", "orders", "workTimes", "services"})
    private Provider provider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore // Prevent recursive fetching with Order
    private Order order;

    @OneToMany(mappedBy = "response", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties({"response", "hibernateLazyInitializer", "handler"})
    private List<SparePartsPrice> sparePartsPrices;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private ResponsesState responsesState;

    @Column(precision = 19, scale = 2)
    private BigDecimal totalPrice;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}