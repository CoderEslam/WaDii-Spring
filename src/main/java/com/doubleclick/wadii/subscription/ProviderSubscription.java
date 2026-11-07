package com.doubleclick.wadii.subscription;

import com.doubleclick.wadii.common.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Billing cycle of one provider. The status is not stored: it is derived from the price in force, the exemption,
 * the due date and the grace period (see {@link SubscriptionService#statusOf}). The notified-* columns only make
 * reminders and notices idempotent per due date.
 */
@Getter
@Setter
@Entity
@Table(name = "provider_subscriptions")
public class ProviderSubscription extends AuditedEntity {

    @Column(name = "provider_id", nullable = false, updatable = false)
    private Long providerId;

    @Column(name = "current_period_start")
    private LocalDate currentPeriodStart;

    @Column(name = "next_due_date")
    private LocalDate nextDueDate;

    @Column(name = "exempt_until")
    private LocalDate exemptUntil;

    @Column(name = "last_paid_at")
    private Instant lastPaidAt;

    @Column(name = "last_reminder_due")
    private LocalDate lastReminderDue;

    @Column(name = "last_reminder_days")
    private Integer lastReminderDays;

    @Column(name = "overdue_notified_due")
    private LocalDate overdueNotifiedDue;

    @Column(name = "restricted_notified_due")
    private LocalDate restrictedNotifiedDue;

    @Version
    private long version;
}
