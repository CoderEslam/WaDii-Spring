package com.doubleclick.wadii.chat;

import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService service;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ChatService.ConversationView>>> list(@AuthenticationPrincipal AuthUser me,
                                                                                       @RequestParam(required = false) Integer page,
                                                                                       @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.list(me, page, size));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ChatService.ConversationView>> open(@AuthenticationPrincipal AuthUser me,
                                                                          @Valid @RequestBody ChatService.OpenInput body) {
        return ApiResponse.ok(service.open(me, body));
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<ApiResponse<List<ChatService.MessageView>>> messages(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                               @RequestParam(required = false) Long afterId,
                                                                               @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(service.messages(me, id, afterId, limit));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<ApiResponse<ChatService.MessageView>> send(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                     @Valid @RequestBody ChatService.SendInput body) {
        return ApiResponse.created(service.send(me, id, body), "Sent");
    }
}
