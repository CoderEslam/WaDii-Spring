package com.doubleclick.wadii.audit;

import com.doubleclick.wadii.common.security.AccessDeniedRecorded;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.common.security.RequestInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes audit entries for the current caller. Joins the surrounding transaction so a rollback drops the entry too. */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;

    @Transactional
    public void record(String action, String entityType, Long entityId, String details) {
        Long actorId = RequestInfo.currentUser().map(AuthUser::id).orElse(null);
        repository.save(new AuditLog(actorId, action, entityType, entityId, details, RequestInfo.clientIp()));
    }

    /** Refused requests are recorded in their own transaction because the request itself fails. */
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAccessDenied(AccessDeniedRecorded event) {
        record("ACCESS_DENIED", "ENDPOINT", null, event.endpoint() + " - " + event.reason());
    }
}
