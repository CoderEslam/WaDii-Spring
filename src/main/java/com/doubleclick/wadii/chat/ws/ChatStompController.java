package com.doubleclick.wadii.chat.ws;

import com.doubleclick.wadii.chat.ChatService;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

/**
 * STOMP inbound destinations (prefix /app). The sender is always the authenticated principal.
 * <pre>
 * /app/chat.send   {conversationId, text}
 * /app/chat.typing {conversationId, typing}
 * /app/chat.read   {conversationId}
 * </pre>
 * Errors go back to /user/queue/errors as {code, message}.
 */
@Controller
@RequiredArgsConstructor
public class ChatStompController {

    private final ChatService chatService;

    public record SendPayload(Long conversationId, String text) {
    }

    public record TypingPayload(Long conversationId, boolean typing) {
    }

    public record ReadPayload(Long conversationId) {
    }

    @MessageMapping("/chat.send")
    public void send(Principal principal, @Payload SendPayload payload) {
        chatService.sendText(user(principal), payload.conversationId(), payload.text());
    }

    @MessageMapping("/chat.typing")
    public void typing(Principal principal, @Payload TypingPayload payload) {
        chatService.typing(user(principal), payload.conversationId(), payload.typing());
    }

    @MessageMapping("/chat.read")
    public void read(Principal principal, @Payload ReadPayload payload) {
        chatService.markRead(user(principal), payload.conversationId());
    }

    @MessageExceptionHandler(BusinessException.class)
    @SendToUser("/queue/errors")
    public Map<String, Object> onError(BusinessException e) {
        return Map.of("code", e.code().name(), "message", e.getMessage());
    }

    private static AuthUser user(Principal principal) {
        if (principal instanceof WebSocketConfig.StompPrincipal p) return p.user();
        if (principal instanceof org.springframework.security.authentication.UsernamePasswordAuthenticationToken t
                && t.getPrincipal() instanceof AuthUser u) return u;
        throw BusinessException.forbidden("Authentication required");
    }
}
