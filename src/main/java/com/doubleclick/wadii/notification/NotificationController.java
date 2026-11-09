package com.doubleclick.wadii.notification;

import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NotificationService.View>>> list(
            @AuthenticationPrincipal AuthUser me, @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.list(me.id(), unreadOnly, page, size));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unread(@AuthenticationPrincipal AuthUser me) {
        return ApiResponse.ok(Map.of("count", service.unreadCount(me.id())));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> read(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        service.markRead(me.id(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> readAll(@AuthenticationPrincipal AuthUser me) {
        service.markAllRead(me.id());
        return ApiResponse.ok(null);
    }
}
