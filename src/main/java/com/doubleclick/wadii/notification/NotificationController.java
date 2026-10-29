package com.doubleclick.wadii.notification;

import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.Map;

/** The caller's own inbox. Ownership comes from the JWT only. */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository repository;
    private final Clock clock;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PageResponse<NotificationView>>> list(@AuthenticationPrincipal AuthUser me,
                                                                            @RequestParam(defaultValue = "false") boolean unreadOnly,
                                                                            @RequestParam(required = false) Integer page,
                                                                            @RequestParam(required = false) Integer size) {
        var pageable = PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = unreadOnly ? repository.findByUserIdAndReadAtIsNull(me.id(), pageable) : repository.findByUserId(me.id(), pageable);
        return ApiResponse.ok(PageResponse.of(result, NotificationView::of));
    }

    @GetMapping("/unread-count")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount(@AuthenticationPrincipal AuthUser me) {
        return ApiResponse.ok(Map.of("count", repository.countByUserIdAndReadAtIsNull(me.id())));
    }

    @PostMapping("/{id}/read")
    @Transactional
    public ResponseEntity<ApiResponse<NotificationView>> markRead(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        Notification n = repository.findById(id).orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND));
        if (!n.getUserId().equals(me.id())) {
            throw BusinessException.forbidden();
        }
        if (n.getReadAt() == null) {
            n.setReadAt(clock.instant());
        }
        return ApiResponse.ok(NotificationView.of(n), "Marked as read");
    }

    @PostMapping("/read-all")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllRead(@AuthenticationPrincipal AuthUser me) {
        return ApiResponse.ok(Map.of("updated", repository.markAllRead(me.id(), clock.instant())), "All marked as read");
    }
}
