package com.doubleclick.wadii.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PresenceEventListener {

    private final PresenceRegistry registry;
    private final SimpMessagingTemplate messagingTemplate;
    private final com.doubleclick.wadii.service.ChatAccessService chatAccessService;

    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        Principal user = event.getUser();
        if (user == null) return;
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = sha.getSessionId();
        Long userId = Long.parseLong(user.getName());
        registry.connect(userId, sessionId);
        publish(userId, true);
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        Principal user = event.getUser();
        if (user == null) return;
        String sessionId = event.getSessionId();
        Long userId = Long.parseLong(user.getName());
        registry.disconnect(sessionId);
        // Only broadcast offline when the user's last session closes
        if (!registry.isOnline(userId)) {
            publish(userId, false);
        }
    }

    /** Presence is only shared with users connected through an accepted order (queue: /user/queue/presence). */
    private void publish(Long userId, boolean online) {
        for (Long counterpart : chatAccessService.counterparts(userId)) {
            messagingTemplate.convertAndSendToUser(String.valueOf(counterpart), "/queue/presence",
                    Map.of("userId", userId, "online", online));
        }
    }
}
