package com.doubleclick.wadii.audit;

import com.doubleclick.wadii.common.security.AccessDeniedRecorded;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.common.security.RequestInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Records every authorization failure (IDOR attempts included) and checks the repeated-failure fraud rule. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessDeniedAuditListener {

    private final AuditService auditService;
    private final FraudService fraudService;

    @EventListener
    public void onAccessDenied(AccessDeniedRecorded event) {
        AuthUser user = RequestInfo.currentUser().orElse(null);
        try {
            auditService.securityEvent(user == null ? null : user.id(), AuditService.roleOf(user),
                    AuditAction.AUTHORIZATION_FAILURE, event.endpoint() + " - " + event.reason());
            fraudService.checkAuthorizationFailures(user == null ? null : user.id());
        } catch (Exception e) {
            log.warn("Could not record authorization failure: {}", e.getMessage());
        }
    }
}
