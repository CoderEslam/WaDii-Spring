package com.doubleclick.wadii.identity;

import com.doubleclick.wadii.audit.AuditAction;
import com.doubleclick.wadii.audit.AuditService;
import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.common.security.Permissions;
import com.doubleclick.wadii.identity.dto.CreateAdminRequest;
import com.doubleclick.wadii.identity.dto.UserView;
import com.doubleclick.wadii.order.ActorRole;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Controlled administrative account management. Admins can only be created here, by a SUPER_ADMIN. */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public record AdminRolesRequest(@NotEmpty Set<AdminRole> adminRoles) {
    }

    public record StatusRequest(@NotNull AccountStatus status, String reason) {
    }

    @GetMapping
    @PreAuthorize(Permissions.SUPPORT_ADMIN)
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PageResponse<UserView>>> list(@RequestParam(required = false) Role role,
                                                                    @RequestParam(required = false) String q,
                                                                    @RequestParam(required = false) Integer page,
                                                                    @RequestParam(required = false) Integer size) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (role != null) p.add(cb.equal(root.get("role"), role));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(root.get("firstName")), like),
                        cb.like(cb.lower(root.get("lastName")), like)));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        var result = userRepository.findAll(spec, PageResponse.pageable(page, size, Sort.by("id")));
        return ApiResponse.ok(PageResponse.of(result, UserView::of));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permissions.SUPPORT_ADMIN)
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<UserView>> get(@PathVariable Long id) {
        return ApiResponse.ok(UserView.of(find(id)));
    }

    @PostMapping("/admins")
    @PreAuthorize(Permissions.SUPER_ADMIN)
    @Transactional
    public ResponseEntity<ApiResponse<UserView>> createAdmin(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CreateAdminRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email().trim())) {
            throw BusinessException.of(ErrorCode.EMAIL_ALREADY_USED);
        }
        User admin = new User();
        admin.setEmail(request.email().trim());
        admin.setPasswordHash(passwordEncoder.encode(request.password()));
        admin.setFirstName(request.firstName().trim());
        admin.setLastName(request.lastName());
        admin.setRole(Role.ADMIN);
        admin.getAdminRoles().addAll(request.adminRoles());
        admin = userRepository.save(admin);
        auditService.record(me, ActorRole.ADMIN, AuditAction.ROLE_CHANGE, "USER", admin.getId(), "admin created with " + request.adminRoles());
        return ApiResponse.created(UserView.of(admin), "Admin created");
    }

    @PutMapping("/{id}/admin-roles")
    @PreAuthorize(Permissions.SUPER_ADMIN)
    @Transactional
    public ResponseEntity<ApiResponse<UserView>> setAdminRoles(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                               @Valid @RequestBody AdminRolesRequest request) {
        User user = find(id);
        if (user.getRole() != Role.ADMIN) {
            throw BusinessException.validation("Only admin accounts hold admin roles");
        }
        if (user.getId().equals(me.id()) && !request.adminRoles().contains(AdminRole.SUPER_ADMIN)) {
            throw BusinessException.of(ErrorCode.CONFLICT, "You cannot remove your own SUPER_ADMIN role");
        }
        Set<AdminRole> before = Set.copyOf(user.getAdminRoles());
        user.getAdminRoles().clear();
        user.getAdminRoles().addAll(request.adminRoles());
        user.revokeTokens();
        auditService.record(me, ActorRole.ADMIN, AuditAction.ROLE_CHANGE, "USER", id, before + " -> " + request.adminRoles());
        return ApiResponse.ok(UserView.of(user), "Admin roles updated");
    }

    @PutMapping("/{id}/status")
    @PreAuthorize(Permissions.SUPPORT_ADMIN)
    @Transactional
    public ResponseEntity<ApiResponse<UserView>> setStatus(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                           @Valid @RequestBody StatusRequest request) {
        User user = find(id);
        if (user.getId().equals(me.id())) {
            throw BusinessException.of(ErrorCode.CONFLICT, "You cannot change your own account status");
        }
        if (user.getRole() == Role.ADMIN && !me.hasAdminRole(AdminRole.SUPER_ADMIN)) {
            throw BusinessException.forbidden("Only a SUPER_ADMIN can change an admin account");
        }
        AccountStatus before = user.getStatus();
        user.setStatus(request.status());
        if (request.status() != AccountStatus.ACTIVE) {
            user.revokeTokens();
        }
        auditService.record(me, ActorRole.ADMIN, AuditAction.ACCOUNT_STATUS_CHANGE, "USER", id,
                before + " -> " + request.status() + (request.reason() == null ? "" : " (" + request.reason() + ")"));
        return ApiResponse.ok(UserView.of(user), "Status updated");
    }

    private User find(Long id) {
        return userRepository.findById(id).orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND));
    }
}
