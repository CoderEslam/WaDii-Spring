package com.doubleclick.wadii.chat;

import com.doubleclick.wadii.chat.CallService.CallSession;
import com.doubleclick.wadii.chat.CallService.CallView;
import com.doubleclick.wadii.chat.ChatService.ConversationView;
import com.doubleclick.wadii.chat.ChatService.MessageView;
import com.doubleclick.wadii.common.api.ApiResponse;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final CallService callService;

    public record TextRequest(@NotBlank @Size(max = 4000) String text) {
    }

    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<PageResponse<ConversationView>>> conversations(@AuthenticationPrincipal AuthUser me,
                                                                                     @RequestParam(required = false) Integer page,
                                                                                     @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(chatService.conversations(me, page, size));
    }

    @GetMapping("/orders/{orderId}/conversation")
    public ResponseEntity<ApiResponse<ConversationView>> forOrder(@AuthenticationPrincipal AuthUser me, @PathVariable Long orderId) {
        return ApiResponse.ok(chatService.forOrder(me, orderId));
    }

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<ApiResponse<PageResponse<MessageView>>> messages(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                                           @RequestParam(required = false) Integer page,
                                                                           @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(chatService.messages(me, id, page, size));
    }

    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<ApiResponse<MessageView>> send(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                         @Valid @RequestBody TextRequest request) {
        return ApiResponse.created(chatService.sendText(me, id, request.text()), "Message sent");
    }

    @PostMapping(value = "/conversations/{id}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MessageView>> sendMedia(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                              @RequestParam("file") MultipartFile file,
                                                              @RequestParam(required = false) String caption) {
        return ApiResponse.created(chatService.sendMedia(me, id, file, caption), "Message sent");
    }

    @PostMapping("/conversations/{id}/read")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> read(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return ApiResponse.ok(Map.of("updated", chatService.markRead(me, id)));
    }

    @GetMapping("/conversations/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unread(@AuthenticationPrincipal AuthUser me) {
        return ApiResponse.ok(Map.of("count", chatService.unreadCount(me)));
    }

    @GetMapping("/messages/{messageId}/file")
    public ResponseEntity<Resource> media(@AuthenticationPrincipal AuthUser me, @PathVariable Long messageId) {
        return chatService.mediaFile(me, messageId);
    }

    // ---------------------------------------------------------------- calls

    @PostMapping("/conversations/{id}/calls")
    public ResponseEntity<ApiResponse<CallSession>> startCall(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                                              @RequestParam(defaultValue = "AUDIO") CallType type,
                                                              @RequestParam(defaultValue = "AGORA") RtcTokenService.Vendor vendor) {
        return ApiResponse.created(callService.start(me, id, type, vendor), "Calling");
    }

    @PostMapping("/calls/{callId}/accept")
    public ResponseEntity<ApiResponse<CallSession>> acceptCall(@AuthenticationPrincipal AuthUser me, @PathVariable Long callId,
                                                               @RequestParam(defaultValue = "AGORA") RtcTokenService.Vendor vendor) {
        return ApiResponse.ok(callService.accept(me, callId, vendor), "Call accepted");
    }

    @PostMapping("/calls/{callId}/reject")
    public ResponseEntity<ApiResponse<CallView>> rejectCall(@AuthenticationPrincipal AuthUser me, @PathVariable Long callId) {
        return ApiResponse.ok(callService.reject(me, callId), "Call rejected");
    }

    @PostMapping("/calls/{callId}/end")
    public ResponseEntity<ApiResponse<CallView>> endCall(@AuthenticationPrincipal AuthUser me, @PathVariable Long callId) {
        return ApiResponse.ok(callService.end(me, callId), "Call ended");
    }

    @PostMapping("/calls/{callId}/token")
    public ResponseEntity<ApiResponse<RtcTokenService.RtcToken>> callToken(@AuthenticationPrincipal AuthUser me, @PathVariable Long callId,
                                                                           @RequestParam(defaultValue = "AGORA") RtcTokenService.Vendor vendor) {
        return ApiResponse.ok(callService.token(me, callId, vendor));
    }
}
