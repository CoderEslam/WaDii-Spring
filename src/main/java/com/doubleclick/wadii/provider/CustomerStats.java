package com.doubleclick.wadii.provider;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Analytical customer behaviour data. V1 never uses it to restrict customers. */
@Entity
@Table(name = "customer_stats")
@Getter
@Setter
@NoArgsConstructor
public class CustomerStats {

    @Id
    @Column(name = "user_id")
    private Long userId;

    private int ordersCount;
    private int completedCount;
    private int canceledCount;
    private int noShowCount;
    private int disputeCount;
    @Column(nullable = false)
    private Instant updatedAt;

    public CustomerStats(Long userId) {
        this.userId = userId;
        this.updatedAt = Instant.now();
    }
}
