package com.doubleclick.wadii.subscription;

import com.doubleclick.wadii.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** History of exemptions granted by finance, with the reason. */
@Getter
@Entity
@Table(name = "subscription_exemptions")
@NoArgsConstructor
public class SubscriptionExemption extends BaseEntity {

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "until_date", nullable = false)
    private LocalDate untilDate;

    @Column(nullable = false)
    private String reason;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    public SubscriptionExemption(Long providerId, LocalDate untilDate, String reason, Long createdBy) {
        this.providerId = providerId;
        this.untilDate = untilDate;
        this.reason = reason;
        this.createdBy = createdBy;
    }
}
