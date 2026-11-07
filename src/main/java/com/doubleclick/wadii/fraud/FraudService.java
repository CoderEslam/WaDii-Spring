package com.doubleclick.wadii.fraud;

import com.doubleclick.wadii.audit.AuditService;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.dispute.DisputeRepository;
import com.doubleclick.wadii.order.ServiceOrder;
import com.doubleclick.wadii.order.ServiceOrderRepository;
import com.doubleclick.wadii.settings.Configs.MarketplaceConfig;
import com.doubleclick.wadii.settings.PlatformConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;

/**
 * Rule-based signals for staff review (30-day windows, thresholds from the MARKETPLACE configuration). Raising a
 * flag never suspends, blocks or hides anything; only an admin action does, and that action is audited.
 */
@Service
@RequiredArgsConstructor
public class FraudService {

    private static final Duration WINDOW = Duration.ofDays(30);

    private final FraudFlagRepository flags;
    private final ServiceOrderRepository orders;
    private final DisputeRepository disputes;
    private final PlatformConfigService config;
    private final AuditService audit;
    private final Clock clock;

    public record View(Long id, FraudFlag.Type type, Long subjectUserId, String details, FraudFlag.Status status,
                       Long reviewedBy, String reviewNote, Instant reviewedAt, Instant createdAt) {
        static View of(FraudFlag f) {
            return new View(f.getId(), f.getType(), f.getSubjectUserId(), f.getDetails(), f.getStatus(), f.getReviewedBy(),
                    f.getReviewNote(), f.getReviewedAt(), f.getCreatedAt());
        }
    }

    @Transactional
    public void afterProviderFault(Long providerId, Long providerUserId) {
        MarketplaceConfig cfg = config.marketplace();
        long count = orders.countCancellations(EnumSet.of(ServiceOrder.CancellationKind.PROVIDER_CANCELED,
                ServiceOrder.CancellationKind.PROVIDER_NO_SHOW), since(), providerId, null);
        if (count >= cfg.providerCancellationFlagThreshold()) {
            raise(FraudFlag.Type.PROVIDER_CANCELLATIONS, providerUserId,
                    count + " provider cancellations or no-shows in the last 30 days (provider " + providerId + ").");
        }
    }

    @Transactional
    public void afterCustomerCancellation(Long customerId) {
        MarketplaceConfig cfg = config.marketplace();
        long count = orders.countCancellations(EnumSet.of(ServiceOrder.CancellationKind.CUSTOMER_CANCELED,
                ServiceOrder.CancellationKind.CUSTOMER_NO_SHOW), since(), null, customerId);
        if (count >= cfg.customerCancellationFlagThreshold()) {
            raise(FraudFlag.Type.CUSTOMER_CANCELLATIONS, customerId, count + " customer cancellations or no-shows in the last 30 days.");
        }
    }

    @Transactional
    public void afterDisputeOpened(Long userId) {
        long count = disputes.countByOpenedByAndCreatedAtAfter(userId, since());
        if (count >= config.marketplace().disputeFlagThreshold()) {
            raise(FraudFlag.Type.EXCESSIVE_DISPUTES, userId, count + " disputes opened in the last 30 days.");
        }
    }

    @Transactional
    public void cashConflict(ServiceOrder order) {
        raise(FraudFlag.Type.CASH_CONFLICT, order.getProviderUserId(),
                "Customer disputed the cash reported as collected on order " + order.getId() + ".");
    }

    @Transactional
    public void duplicateListing(Long sellerId, String title) {
        raise(FraudFlag.Type.DUPLICATE_LISTING, sellerId, "Several active listings titled \"" + title + "\".");
    }

    @Transactional
    public View manual(AuthUser admin, Long subjectUserId, String note) {
        FraudFlag f = new FraudFlag();
        f.setType(FraudFlag.Type.MANUAL);
        f.setSubjectUserId(subjectUserId);
        f.setDetails(note);
        flags.save(f);
        audit.record("FRAUD_FLAG_MANUAL", "USER", subjectUserId, note);
        return View.of(f);
    }

    @Transactional(readOnly = true)
    public PageResponse<View> list(FraudFlag.Status status, Integer page, Integer size) {
        return PageResponse.of(flags.search(status, PageResponse.pageable(page, size)), View::of);
    }

    @Transactional
    public View review(AuthUser admin, Long id, FraudFlag.Status decision, String note) {
        FraudFlag f = flags.findById(id).orElseThrow(() -> BusinessException.of(ErrorCode.NOT_FOUND));
        if (f.getStatus() != FraudFlag.Status.OPEN) {
            throw BusinessException.of(ErrorCode.CONFLICT, "This flag was already reviewed.");
        }
        f.setStatus(decision);
        f.setReviewedBy(admin.id());
        f.setReviewNote(note);
        f.setReviewedAt(Instant.now(clock));
        audit.record("FRAUD_FLAG_" + decision, "FRAUD_FLAG", id, note);
        return View.of(f);
    }

    /** One open flag per type and subject is enough; repeats would only add noise for reviewers. */
    private void raise(FraudFlag.Type type, Long subjectUserId, String details) {
        if (flags.existsByTypeAndSubjectUserIdAndStatus(type, subjectUserId, FraudFlag.Status.OPEN)) {
            return;
        }
        FraudFlag f = new FraudFlag();
        f.setType(type);
        f.setSubjectUserId(subjectUserId);
        f.setDetails(details);
        flags.save(f);
    }

    private Instant since() {
        return Instant.now(clock).minus(WINDOW);
    }
}
