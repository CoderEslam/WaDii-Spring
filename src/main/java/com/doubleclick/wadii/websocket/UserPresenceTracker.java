package com.doubleclick.wadii.websocket;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserPresenceTracker {

    private final Set<Long> onlineUsers = ConcurrentHashMap.newKeySet();

    @EventListener
    public void handleConnect(SessionConnectedEvent event) {
        extractUserId(event.getUser()).ifPresent(onlineUsers::add);
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        extractUserId(event.getUser()).ifPresent(onlineUsers::remove);
    }

    public boolean isOnline(Long userId) {
        return onlineUsers.contains(userId);
    }

    private Optional<Long> extractUserId(Principal principal) {
        if (principal == null) return Optional.empty();
        try {
            return Optional.of(Long.parseLong(principal.getName()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
