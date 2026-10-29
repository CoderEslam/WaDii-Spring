package com.doubleclick.wadii.audit;

import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.common.security.Permissions;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminAuditController {

    private final AuditLogRepository auditLogRepository;
    private final FraudFlagRepository fraudFlagRepository;

    public record AuditLogView(Long id, Long actorUserId, String actorRole, AuditAction action, String entityType,
                               Long entityId, String details, String ipAddress, Instant createdAt) {
        static AuditLogView of(AuditLog a) {
            return new AuditLogView(a.getId(), a.getActorUserId(), a.getActorRole().name(), a.getAction(), a.getEntityType(),
                    a.getEntityId(), a.getDetails(), a.getIpAddress(), a.getCreatedAt());
        }
    }

    public record FraudFlagView(Long id, Long subjectUserId, FraudFlagType type, String entityType, Long entityId,
                                String details, FraudFlagStatus status, Long reviewedBy, Instant reviewedAt, Instant createdAt) {
        static FraudFlagView of(FraudFlag f) {
            return new FraudFlagView(f.getId(), f.getSubjectUserId(), f.getType(), f.getEntityType(), f.getEntityId(),
                    f.getDetails(), f.getStatus(), f.getReviewedBy(), f.getReviewedAt(), f.getCreatedAt());
        }
    }

    @GetMapping("/audit-logs")
    @PreAuthorize(Permissions.SUPER_ADMIN + " or " + Permissions.FINANCE_ADMIN + " or " + Permissions.SUPPORT_ADMIN)
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PageResponse<AuditLogView>>> auditLogs(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Specification<AuditLog> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (action != null) p.add(cb.equal(root.get("action"), action));
            if (entityType != null) p.add(cb.equal(root.get("entityType"), entityType));
            if (entityId != null) p.add(cb.equal(root.get("entityId"), entityId));
            if (actorUserId != null) p.add(cb.equal(root.get("actorUserId"), actorUserId));
            return cb.and(p.toArray(new Predicate[0]));
        };
        var result = auditLogRepository.findAll(spec, PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ApiResponse.ok(PageResponse.of(result, AuditLogView::of));
    }

    @GetMapping("/fraud-flags")
    @PreAuthorize(Permissions.SUPPORT_OR_FINANCE)
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PageResponse<FraudFlagView>>> fraudFlags(
            @RequestParam(defaultValue = "OPEN") FraudFlagStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        var result = fraudFlagRepository.findByStatus(status, PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ApiResponse.ok(PageResponse.of(result, FraudFlagView::of));
    }

    @PostMapping("/fraud-flags/{id}/review")
    @PreAuthorize(Permissions.SUPPORT_OR_FINANCE)
    @Transactional
    public ResponseEntity<ApiResponse<FraudFlagView>> review(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                             @RequestParam(defaultValue = "REVIEWED") FraudFlagStatus status) {
        if (status == FraudFlagStatus.OPEN) {
            throw BusinessException.validation("status must be REVIEWED or DISMISSED");
        }
        FraudFlag flag = fraudFlagRepository.findById(id).orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND));
        flag.setStatus(status);
        flag.setReviewedBy(me.id());
        flag.setReviewedAt(Instant.now());
        return ApiResponse.ok(FraudFlagView.of(flag), "Flag updated");
    }
}
