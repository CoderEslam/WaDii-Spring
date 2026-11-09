package com.doubleclick.wadii.subscription;

import com.doubleclick.wadii.audit.AuditService;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.common.util.Money;
import com.doubleclick.wadii.common.util.Times;
import com.doubleclick.wadii.ledger.LedgerEntry;
import com.doubleclick.wadii.ledger.LedgerRepository;
import com.doubleclick.wadii.ledger.LedgerService;
import com.doubleclick.wadii.notification.NotificationService;
import com.doubleclick.wadii.notification.NotificationType;
import com.doubleclick.wadii.provider.Provider;
import com.doubleclick.wadii.provider.ProviderRepository;
import com.doubleclick.wadii.settings.Configs.SubscriptionConfig;
import com.doubleclick.wadii.settings.PlatformConfigService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provider monthly subscription. A price of zero means free with no restriction. A cycle starts on the actual
 * payment date and the next due date is one calendar month later; a payment on the 31st falls due on the last day
 * of a shorter month. After the due date the provider keeps full access during the grace period (PAYMENT_OVERDUE)
 * and is then RESTRICTED from new provider activity only: customer mode and existing orders are untouched.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    public enum Status { FREE_OR_DISABLED, ACTIVE, REMINDER_DUE, PAYMENT_OVERDUE, RESTRICTED, EXEMPT }

    private final ProviderSubscriptionRepository repository;
    private final SubscriptionExemptionRepository exemptions;
    private final ProviderRepository providers;
    private final LedgerService ledger;
    private final LedgerRepository ledgerRepository;
    private final PlatformConfigService config;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    public record View(Status status, BigDecimal monthlyPrice, LocalDate currentPeriodStart, LocalDate nextDueDate,
                       LocalDate graceEndsOn, LocalDate exemptUntil, Instant lastPaidAt) {
    }

    public record AdminRow(Long providerId, String businessName, Status status, BigDecimal monthlyPrice,
                           LocalDate currentPeriodStart, LocalDate nextDueDate, LocalDate graceEndsOn,
                           LocalDate exemptUntil, Instant lastPaidAt) {
    }

    public record PaymentRequest(@NotNull @Positive BigDecimal amount, @NotNull LocalDate paidOn,
                                 @NotNull LedgerEntry.Method method, @Size(max = 120) String reference) {
    }

    public record ExemptionRequest(@Min(1) @Max(36) Integer months, LocalDate until, @NotBlank @Size(max = 500) String reason) {
    }

    @Transactional
    public ProviderSubscription ensure(Long providerId) {
        return repository.findByProviderId(providerId).orElseGet(() -> {
            ProviderSubscription s = new ProviderSubscription();
            s.setProviderId(providerId);
            return repository.save(s);
        });
    }

    @Transactional(readOnly = true)
    public View view(Long providerId) {
        ProviderSubscription s = repository.findByProviderId(providerId).orElseGet(() -> {
            ProviderSubscription empty = new ProviderSubscription();
            empty.setProviderId(providerId);
            return empty;
        });
        return view(s, config.subscription().value(), Times.today(clock));
    }

    /** True when new provider activity (receiving requests, sending offers) must be blocked. */
    @Transactional(readOnly = true)
    public boolean isRestricted(Long providerId) {
        return view(providerId).status() == Status.RESTRICTED;
    }

    @Transactional(readOnly = true)
    public Map<Long, Boolean> restrictedByProvider(List<Long> providerIds) {
        SubscriptionConfig cfg = config.subscription().value();
        LocalDate today = Times.today(clock);
        Map<Long, ProviderSubscription> byProvider = repository.findByProviderIdIn(providerIds).stream()
                .collect(Collectors.toMap(ProviderSubscription::getProviderId, Function.identity()));
        return providerIds.stream().collect(Collectors.toMap(Function.identity(), id -> {
            ProviderSubscription s = byProvider.get(id);
            return s != null && view(s, cfg, today).status() == Status.RESTRICTED;
        }));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminRow> adminList(Status status, Integer page, Integer size) {
        SubscriptionConfig cfg = config.subscription().value();
        LocalDate today = Times.today(clock);
        List<Provider> verified = providers.findByApplicationStatusOrderBySubmittedAtAsc(Provider.ApplicationStatus.VERIFIED,
                PageRequest.of(0, 10_000)).getContent();
        Map<Long, ProviderSubscription> subs = repository.findByProviderIdIn(verified.stream().map(Provider::getId).toList())
                .stream().collect(Collectors.toMap(ProviderSubscription::getProviderId, Function.identity()));
        List<AdminRow> rows = verified.stream().map(p -> {
            ProviderSubscription s = subs.getOrDefault(p.getId(), new ProviderSubscription());
            View v = view(s, cfg, today);
            return new AdminRow(p.getId(), p.getBusinessName(), v.status(), v.monthlyPrice(), v.currentPeriodStart(),
                    v.nextDueDate(), v.graceEndsOn(), v.exemptUntil(), v.lastPaidAt());
        }).filter(r -> status == null || r.status() == status).toList();
        var pageable = PageResponse.pageable(page, size);
        int from = (int) Math.min(pageable.getOffset(), rows.size());
        int to = Math.min(from + pageable.getPageSize(), rows.size());
        int totalPages = (rows.size() + pageable.getPageSize() - 1) / pageable.getPageSize();
        return new PageResponse<>(rows.subList(from, to), pageable.getPageNumber(), pageable.getPageSize(), rows.size(), totalPages);
    }

    /** Finance records a payment received outside the app; the open subscription entry is settled and the cycle renewed. */
    @Transactional
    public View recordPayment(AuthUser admin, Long providerId, PaymentRequest request) {
        Provider provider = providers.findById(providerId).orElseThrow(() -> BusinessException.of(ErrorCode.PROVIDER_NOT_FOUND));
        if (request.paidOn().isAfter(Times.today(clock))) {
            throw BusinessException.validation("paidOn cannot be in the future.");
        }
        LedgerEntry entry = ledgerRepository.search(provider.getUserId(), providerId, LedgerEntry.Type.SUBSCRIPTION,
                        null, PageRequest.of(0, 50)).stream()
                .filter(LedgerEntry::isOpen).findFirst()
                .orElseGet(() -> ledger.due(new LedgerService.Charge(LedgerEntry.Type.SUBSCRIPTION, request.amount(),
                        provider.getUserId(), providerId, null, null, null, "Monthly provider subscription (recorded payment)",
                        config.subscription().versionId(), request.paidOn())));
        ledger.settle(entry.getId(), new LedgerService.SettleRequest(request.method(), request.reference(), "Recorded by finance"));
        renew(providerId, request.paidOn());
        audit.record("SUBSCRIPTION_PAYMENT_RECORDED", "PROVIDER", providerId, request.amount() + " on " + request.paidOn());
        return view(providerId);
    }

    @EventListener
    @Transactional
    public void onSettled(LedgerService.EntrySettled event) {
        if (event.type() == LedgerEntry.Type.SUBSCRIPTION && event.providerId() != null) {
            renew(event.providerId(), event.paidOn());
        }
    }

    @Transactional
    public View exempt(AuthUser admin, Long providerId, ExemptionRequest request) {
        if (!providers.existsById(providerId)) {
            throw BusinessException.of(ErrorCode.PROVIDER_NOT_FOUND);
        }
        LocalDate today = Times.today(clock);
        LocalDate until;
        if (request.until() != null) {
            until = request.until();
        } else if (request.months() != null) {
            until = today.plusMonths(request.months());
        } else {
            throw BusinessException.validation("Give either months or until.");
        }
        if (until.isBefore(today)) {
            throw BusinessException.validation("until must not be in the past.");
        }
        ProviderSubscription s = locked(providerId);
        s.setExemptUntil(until);
        if (s.getNextDueDate() == null || !s.getNextDueDate().isAfter(until)) {
            s.setNextDueDate(until.plusDays(1));
        }
        exemptions.save(new SubscriptionExemption(providerId, until, request.reason().trim(), admin.id()));
        audit.record("SUBSCRIPTION_EXEMPTED", "PROVIDER", providerId, "until " + until + ": " + request.reason());
        return view(providerId);
    }

    /** Reminders 7 and 3 days before (configurable), a DUE ledger entry on the due date, overdue and restriction notices. */
    @Scheduled(fixedDelayString = "${wadii.jobs.subscription-interval-ms:3600000}", initialDelay = 30_000)
    @Transactional
    public void runDaily() {
        var versioned = config.subscription();
        SubscriptionConfig cfg = versioned.value();
        if (!Money.isPositive(cfg.monthlyPrice())) {
            return;
        }
        LocalDate today = Times.today(clock);
        List<Provider> verified = providers.findByApplicationStatusOrderBySubmittedAtAsc(Provider.ApplicationStatus.VERIFIED,
                PageRequest.of(0, 10_000)).getContent();
        for (Provider p : verified) {
            ProviderSubscription s = ensure(p.getId());
            if (s.getExemptUntil() != null && !today.isAfter(s.getExemptUntil())) {
                continue;
            }
            if (s.getNextDueDate() == null) {
                s.setNextDueDate(today);
            }
            LocalDate due = s.getNextDueDate();
            long daysLeft = ChronoUnit.DAYS.between(today, due);
            if (daysLeft > 0 && cfg.reminderDaysBefore().contains((int) daysLeft)
                    && !(due.equals(s.getLastReminderDue()) && Integer.valueOf((int) daysLeft).equals(s.getLastReminderDays()))) {
                s.setLastReminderDue(due);
                s.setLastReminderDays((int) daysLeft);
                notifications.notify(p.getUserId(), NotificationType.SUBSCRIPTION_REMINDER, "Subscription renewal",
                        "Your WaDii subscription (EGP " + cfg.monthlyPrice() + ") is due in " + daysLeft + " days, on " + due + ".",
                        "SUBSCRIPTION", p.getId());
            }
            if (!today.isBefore(due) && !due.equals(s.getOverdueNotifiedDue())) {
                s.setOverdueNotifiedDue(due);
                if (!ledgerRepository.existsByProviderIdAndTypeAndDueDate(p.getId(), LedgerEntry.Type.SUBSCRIPTION, due)) {
                    ledger.due(new LedgerService.Charge(LedgerEntry.Type.SUBSCRIPTION, cfg.monthlyPrice(), p.getUserId(),
                            p.getId(), null, null, null, "Monthly provider subscription due " + due, versioned.versionId(), due));
                }
                notifications.notify(p.getUserId(), NotificationType.SUBSCRIPTION_OVERDUE, "Subscription payment due",
                        "Your subscription payment is due. Provider activity stays open for " + cfg.graceDays()
                                + " more days. Customer mode and current orders are not affected.", "SUBSCRIPTION", p.getId());
            }
            if (today.isAfter(due.plusDays(cfg.graceDays())) && !due.equals(s.getRestrictedNotifiedDue())) {
                s.setRestrictedNotifiedDue(due);
                notifications.notify(p.getUserId(), NotificationType.SUBSCRIPTION_RESTRICTED, "Provider activity restricted",
                        "New requests and offers are paused until the subscription is settled. Existing orders continue.",
                        "SUBSCRIPTION", p.getId());
                audit.record("SUBSCRIPTION_RESTRICTED", "PROVIDER", p.getId(), "due " + due);
            }
        }
    }

    private void renew(Long providerId, LocalDate paidOn) {
        ProviderSubscription s = locked(providerId);
        s.setCurrentPeriodStart(paidOn);
        s.setNextDueDate(paidOn.plusMonths(1));
        s.setLastPaidAt(Instant.now(clock));
        providers.findById(providerId).ifPresent(p -> notifications.notify(p.getUserId(), NotificationType.SUBSCRIPTION_PAID,
                "Subscription renewed", "Thank you. Your next subscription payment is due on " + s.getNextDueDate() + ".",
                "SUBSCRIPTION", providerId));
    }

    private ProviderSubscription locked(Long providerId) {
        ensure(providerId);
        return repository.lockByProviderId(providerId).orElseThrow();
    }

    static View view(ProviderSubscription s, SubscriptionConfig cfg, LocalDate today) {
        BigDecimal price = Money.of(cfg.monthlyPrice());
        if (s.getExemptUntil() != null && !today.isAfter(s.getExemptUntil())) {
            return new View(Status.EXEMPT, price, s.getCurrentPeriodStart(), s.getNextDueDate(), null, s.getExemptUntil(), s.getLastPaidAt());
        }
        if (price.signum() == 0) {
            return new View(Status.FREE_OR_DISABLED, price, s.getCurrentPeriodStart(), null, null, s.getExemptUntil(), s.getLastPaidAt());
        }
        LocalDate due = s.getNextDueDate() != null ? s.getNextDueDate() : today;
        LocalDate graceEnd = due.plusDays(cfg.graceDays());
        int maxReminder = cfg.reminderDaysBefore().isEmpty() ? 0 : Collections.max(cfg.reminderDaysBefore());
        Status status;
        if (today.isBefore(due)) {
            status = ChronoUnit.DAYS.between(today, due) <= maxReminder ? Status.REMINDER_DUE : Status.ACTIVE;
        } else {
            status = today.isAfter(graceEnd) ? Status.RESTRICTED : Status.PAYMENT_OVERDUE;
        }
        return new View(status, price, s.getCurrentPeriodStart(), due, graceEnd, s.getExemptUntil(), s.getLastPaidAt());
    }
}
