package com.doubleclick.wadii.order;

import com.doubleclick.wadii.catalog.CarType;
import com.doubleclick.wadii.catalog.CatalogService;
import com.doubleclick.wadii.common.persistence.AuditedEntity;
import com.doubleclick.wadii.identity.User;
import com.doubleclick.wadii.provider.Provider;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private User customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Setter(AccessLevel.NONE)
    private OrderStatus status = OrderStatus.CREATED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "car_type_id")
    private CarType carType;

    @Column(name = "car_description", nullable = false)
    private String carDescription;

    @Column(name = "requested_date", nullable = false)
    private LocalDate requestedDate;

    private String address;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    private String comment;

    /** Provider explicitly chosen by the customer; null means automatic matching. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_provider_id", updatable = false)
    private Provider selectedProvider;

    /** Server-controlled: set only by the atomic accept flow. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_provider_id")
    @Setter(AccessLevel.NONE)
    private Provider acceptedProvider;

    @Column(name = "accepted_response_id")
    @Setter(AccessLevel.NONE)
    private Long acceptedResponseId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "response_deadline", nullable = false)
    private Instant responseDeadline;

    private Instant acceptedAt;
    private Instant startedAt;
    private Instant providerCompletedAt;
    private Instant completedAt;
    private Instant canceledAt;
    private Instant expiredAt;

    @Version
    private long version;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "order_services",
            joinColumns = @JoinColumn(name = "order_id"),
            inverseJoinColumns = @JoinColumn(name = "service_id"))
    private Set<CatalogService> services = new HashSet<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<OrderSparePart> spareParts = new ArrayList<>();

    public boolean isOwnedBy(Long userId) {
        return customer != null && customer.getId().equals(userId);
    }

    public boolean isAcceptedProvider(Long providerId) {
        return acceptedProvider != null && acceptedProvider.getId().equals(providerId);
    }

    public Set<Long> serviceIds() {
        return services.stream().map(CatalogService::getId).collect(Collectors.toSet());
    }

    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }

    /** The only way to change status; callers go through {@code OrderStateMachine}. */
    void transitionTo(OrderStatus next) {
        this.status = next;
    }

    void assignAcceptedResponse(Provider provider, Long responseId, Instant at) {
        this.acceptedProvider = provider;
        this.acceptedResponseId = responseId;
        this.acceptedAt = at;
    }
}
