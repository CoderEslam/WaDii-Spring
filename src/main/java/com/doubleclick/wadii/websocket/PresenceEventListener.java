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

    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        Principal user = event.getUser();
        if (user == null) return;
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = sha.getSessionId();
        Long userId = Long.parseLong(user.getName());
        registry.connect(userId, sessionId);
        messagingTemplate.convertAndSend("/topic/presence",
                Map.of("userId", userId, "online", true));
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
            messagingTemplate.convertAndSend("/topic/presence",
                    Map.of("userId", userId, "online", false));
        }
    }
}
