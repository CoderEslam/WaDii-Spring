package com.doubleclick.wadii.audit;

import com.doubleclick.wadii.common.config.WadiiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Rule-based abuse signals. Flags are for admin review only; nothing is blocked automatically in V1. */
@Slf4j
@Service
@RequiredArgsConstructor
public class FraudService {

    private final FraudFlagRepository flagRepository;
    private final AuditLogRepository auditLogRepository;
    private final WadiiProperties properties;
    private final Clock clock;

    /** Raises a flag unless the same open flag was raised for this user in the last 24 hours. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void flag(Long subjectUserId, FraudFlagType type, String entityType, Long entityId, String details) {
        Instant since = clock.instant().minus(Duration.ofHours(24));
        if (subjectUserId != null && flagRepository.existsOpenSince(subjectUserId, type, since)) {
            return;
        }
        FraudFlag flag = new FraudFlag();
        flag.setSubjectUserId(subjectUserId);
        flag.setType(type);
        flag.setEntityType(entityType);
        flag.setEntityId(entityId);
        flag.setDetails(details);
        flagRepository.save(flag);
        log.warn("Fraud flag {} raised for user {} ({} {}): {}", type, subjectUserId, entityType, entityId, details);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkAuthorizationFailures(Long userId) {
        if (userId == null) return;
        Instant since = clock.instant().minus(Duration.ofHours(1));
        long failures = auditLogRepository.countActionsSince(userId, AuditAction.AUTHORIZATION_FAILURE, since);
        if (failures >= properties.getFraud().getMaxAuthFailuresPerHour()) {
            flag(userId, FraudFlagType.REPEATED_AUTHORIZATION_FAILURES, "USER", userId,
                    failures + " authorization failures in the last hour");
        }
    }
}
