package com.doubleclick.wadii.response;

import com.doubleclick.wadii.common.persistence.AuditedEntity;
import com.doubleclick.wadii.order.Order;
import com.doubleclick.wadii.provider.Provider;
import com.doubleclick.wadii.provider.ProviderBranch;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A provider's offer (quote) on an order. Never deleted: expired/declined offers are business history. */
@Entity
@Table(name = "order_responses")
@Getter
@Setter
@NoArgsConstructor
public class OrderResponse extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false, updatable = false)
    private Provider provider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private ProviderBranch branch;

    @Column(name = "labor_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal laborCost = BigDecimal.ZERO;

    @Column(name = "parts_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal partsCost = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    /** Server computed: labor + parts - discount. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private String description;

    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    @Column(name = "proposed_appointment_at")
    private Instant proposedAppointmentAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResponseStatus status = ResponseStatus.PENDING;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "response", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ResponsePartPrice> partPrices = new ArrayList<>();

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public void changeStatus(ResponseStatus next, Instant at) {
        this.status = next;
        this.statusChangedAt = at;
    }
}
