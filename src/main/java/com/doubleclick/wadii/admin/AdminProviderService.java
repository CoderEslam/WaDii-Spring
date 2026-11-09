package com.doubleclick.wadii.admin;

import com.doubleclick.wadii.audit.AuditService;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.notification.NotificationService;
import com.doubleclick.wadii.notification.NotificationType;
import com.doubleclick.wadii.offer.OfferService;
import com.doubleclick.wadii.provider.Provider;
import com.doubleclick.wadii.provider.ProviderRepository;
import com.doubleclick.wadii.provider.ProviderService;
import com.doubleclick.wadii.provider.ProviderViews.ApplicationView;
import com.doubleclick.wadii.request.RequestTarget;
import com.doubleclick.wadii.request.RequestTargetRepository;
import com.doubleclick.wadii.subscription.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;

/**
 * Provider verification and activation. Suspension affects Provider Mode only: pending offers are withdrawn and
 * open invitations declined, while existing orders continue and stay with support for case-by-case handling.
 */
@Service
@RequiredArgsConstructor
public class AdminProviderService {

    private final ProviderRepository providers;
    private final ProviderService providerService;
    private final OfferService offers;
    private final RequestTargetRepository targets;
    private final SubscriptionService subscriptions;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<ApplicationView> applications(Provider.ApplicationStatus status, Integer page, Integer size) {
        var pageable = PageResponse.pageable(page, size);
        var result = status == null
                ? providers.findByApplicationStatusInOrderBySubmittedAtAsc(EnumSet.of(Provider.ApplicationStatus.SUBMITTED,
                Provider.ApplicationStatus.UNDER_REVIEW), pageable)
                : providers.findByApplicationStatusOrderBySubmittedAtAsc(status, pageable);
        return PageResponse.of(result, providerService::applicationView);
    }

    @Transactional(readOnly = true)
    public ApplicationView application(Long providerId) {
        return providerService.applicationView(provider(providerId));
    }

    @Transactional
    public ApplicationView startReview(AuthUser admin, Long providerId) {
        Provider p = locked(providerId);
        requireApplication(p, Provider.ApplicationStatus.SUBMITTED);
        p.setApplicationStatus(Provider.ApplicationStatus.UNDER_REVIEW);
        audit.record("PROVIDER_REVIEW_STARTED", "PROVIDER", providerId, null);
        notifications.notify(p.getUserId(), NotificationType.APPLICATION_UPDATE, "Application under review",
                "WaDii staff started reviewing your provider application.", "APPLICATION", providerId);
        return providerService.applicationView(p);
    }

    @Transactional
    public ApplicationView approve(AuthUser admin, Long providerId) {
        Provider p = locked(providerId);
        requireApplication(p, Provider.ApplicationStatus.SUBMITTED, Provider.ApplicationStatus.UNDER_REVIEW);
        Instant now = Instant.now(clock);
        p.setApplicationStatus(Provider.ApplicationStatus.VERIFIED);
        p.setReviewedAt(now);
        p.setReviewedBy(admin.id());
        p.setVerifiedAt(now);
        subscriptions.ensure(providerId);
        audit.record("PROVIDER_VERIFIED", "PROVIDER", providerId, null);
        notifications.notify(p.getUserId(), NotificationType.APPLICATION_UPDATE, "Provider Mode is ready",
                "Your application was approved. Add a branch and switch to Provider Mode to start receiving requests.",
                "APPLICATION", providerId);
        return providerService.applicationView(p);
    }

    @Transactional
    public ApplicationView reject(AuthUser admin, Long providerId, String reason) {
        Provider p = locked(providerId);
        requireApplication(p, Provider.ApplicationStatus.SUBMITTED, Provider.ApplicationStatus.UNDER_REVIEW);
        p.setApplicationStatus(Provider.ApplicationStatus.REJECTED);
        p.setRejectionReason(reason);
        p.setReviewedAt(Instant.now(clock));
        p.setReviewedBy(admin.id());
        audit.record("PROVIDER_REJECTED", "PROVIDER", providerId, reason);
        notifications.notify(p.getUserId(), NotificationType.APPLICATION_UPDATE, "Application not approved",
                "Reason: " + reason + ". You can update the application and submit it again.", "APPLICATION", providerId);
        return providerService.applicationView(p);
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationView> verified(String q, Provider.Status status, Integer page, Integer size) {
        String pattern = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        return PageResponse.of(providers.searchVerified(pattern, status, PageResponse.pageable(page, size)), providerService::applicationView);
    }

    @Transactional
    public ApplicationView suspend(AuthUser admin, Long providerId, String reason) {
        Provider p = locked(providerId);
        if (!p.isVerified() || p.getProviderStatus() == Provider.Status.SUSPENDED) {
            throw BusinessException.of(ErrorCode.APPLICATION_INVALID_STATE);
        }
        p.setProviderStatus(Provider.Status.SUSPENDED);
        p.setStatusReason(reason);
        offers.withdrawAllFor(providerId);
        targets.findByProviderIdAndStatusIn(providerId, EnumSet.of(RequestTarget.Status.INVITED, RequestTarget.Status.VIEWED))
                .forEach(t -> t.setStatus(RequestTarget.Status.DECLINED));
        audit.record("PROVIDER_SUSPENDED", "PROVIDER", providerId, reason);
        notifications.notify(p.getUserId(), NotificationType.PROVIDER_STATUS, "Provider Mode suspended",
                "Reason: " + reason + ". Your customer account and existing orders are not affected.", "APPLICATION", providerId);
        return providerService.applicationView(p);
    }

    @Transactional
    public ApplicationView reactivate(AuthUser admin, Long providerId, String reason) {
        Provider p = locked(providerId);
        if (p.getProviderStatus() != Provider.Status.SUSPENDED) {
            throw BusinessException.of(ErrorCode.APPLICATION_INVALID_STATE);
        }
        p.setProviderStatus(Provider.Status.ACTIVE);
        p.setStatusReason(reason);
        audit.record("PROVIDER_REACTIVATED", "PROVIDER", providerId, reason);
        notifications.notify(p.getUserId(), NotificationType.PROVIDER_STATUS, "Provider Mode active again",
                "You can receive requests again.", "APPLICATION", providerId);
        return providerService.applicationView(p);
    }

    private Provider provider(Long id) {
        return providers.findById(id).orElseThrow(() -> BusinessException.of(ErrorCode.PROVIDER_NOT_FOUND));
    }

    private Provider locked(Long id) {
        return providers.lockById(id).orElseThrow(() -> BusinessException.of(ErrorCode.PROVIDER_NOT_FOUND));
    }

    private static void requireApplication(Provider p, Provider.ApplicationStatus... allowed) {
        if (!java.util.Arrays.asList(allowed).contains(p.getApplicationStatus())) {
            throw BusinessException.of(ErrorCode.APPLICATION_INVALID_STATE);
        }
    }
}
