package com.doubleclick.wadii.audit;

import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.common.security.RequestInfo;
import com.doubleclick.wadii.order.ActorRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central audit log. Business events are written in the caller's transaction (they commit or roll back with it);
 * security events are written in their own transaction so a rejected request still leaves a trace.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuthUser actor, ActorRole role, AuditAction action, String entityType, Long entityId, String details) {
        save(actor == null ? null : actor.id(), role, action, entityType, entityId, details);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void system(AuditAction action, String entityType, Long entityId, String details) {
        save(null, ActorRole.SYSTEM, action, entityType, entityId, details);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void securityEvent(Long userId, ActorRole role, AuditAction action, String details) {
        save(userId, role, action, null, null, details);
    }

    public static ActorRole roleOf(AuthUser user) {
        if (user == null) return ActorRole.SYSTEM;
        return switch (user.role()) {
            case ADMIN -> ActorRole.ADMIN;
            case PROVIDER -> ActorRole.PROVIDER;
            case USER -> ActorRole.CUSTOMER;
        };
    }

    private void save(Long userId, ActorRole role, AuditAction action, String entityType, Long entityId, String details) {
        AuditLog log = new AuditLog();
        log.setActorUserId(userId);
        log.setActorRole(role);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDetails(details == null || details.length() <= 4000 ? details : details.substring(0, 4000));
        log.setIpAddress(RequestInfo.clientIp());
        repository.save(log);
    }
}
