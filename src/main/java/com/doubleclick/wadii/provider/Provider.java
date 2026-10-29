package com.doubleclick.wadii.provider;

import com.doubleclick.wadii.catalog.CatalogService;
import com.doubleclick.wadii.common.persistence.AuditedEntity;
import com.doubleclick.wadii.identity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Business profile of a PROVIDER account. */
@Entity
@Table(name = "providers")
@Getter
@Setter
@NoArgsConstructor
public class Provider extends AuditedEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProviderStatus status = ProviderStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Column(name = "accepting_orders", nullable = false)
    private boolean acceptingOrders = true;

    @Column(name = "service_radius_km", precision = 7, scale = 2)
    private BigDecimal serviceRadiusKm;

    @Column(name = "rating_avg", nullable = false, precision = 3, scale = 2)
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    @Column(name = "rating_count", nullable = false)
    private int ratingCount;

    @Column(name = "followers_count", nullable = false)
    private int followersCount;

    private Instant verifiedAt;

    @Column(name = "verified_by")
    private Long verifiedBy;

    @Column(name = "status_reason")
    private String statusReason;

    @Version
    private long version;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "provider_services",
            joinColumns = @JoinColumn(name = "provider_id"),
            inverseJoinColumns = @JoinColumn(name = "service_id"))
    private Set<CatalogService> services = new HashSet<>();

    @OneToMany(mappedBy = "provider", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ProviderBranch> branches = new ArrayList<>();

    @OneToMany(mappedBy = "provider", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ProviderLink> links = new ArrayList<>();

    /** Eligible to be targeted by, and respond to, new orders. */
    public boolean isEligibleForOrders() {
        return status == ProviderStatus.ACTIVE
                && verificationStatus == VerificationStatus.VERIFIED
                && acceptingOrders
                && user != null && user.isActive();
    }

    /** ALL-services rule: the provider must offer every requested service. */
    public boolean offersAll(Collection<Long> serviceIds) {
        Set<Long> mine = new HashSet<>();
        services.forEach(s -> mine.add(s.getId()));
        return mine.containsAll(serviceIds);
    }

    public boolean isOwnedBy(Long userId) {
        return user != null && user.getId().equals(userId);
    }
}
