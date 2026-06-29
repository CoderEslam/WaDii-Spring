package com.doubleclick.wadii.websocket;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PresenceRegistry {

    // userId → active sessionIds (supports multiple devices per user)
    private final ConcurrentHashMap<Long, Set<String>> userSessions = new ConcurrentHashMap<>();
    // sessionId → userId for O(1) reverse lookup on disconnect
    private final ConcurrentHashMap<String, Long> sessionToUser = new ConcurrentHashMap<>();

    public void connect(Long userId, String sessionId) {
        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        sessionToUser.put(sessionId, userId);
    }

    /** Returns the userId that owned the session, or null if unknown. */
    public Long disconnect(String sessionId) {
        Long userId = sessionToUser.remove(sessionId);
        if (userId != null) {
            Set<String> sessions = userSessions.get(userId);
            if (sessions != null) {
                sessions.remove(sessionId);
                if (sessions.isEmpty()) {
                    userSessions.remove(userId);
                }
            }
        }
        return userId;
    }

    public boolean isOnline(Long userId) {
        Set<String> sessions = userSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    public Set<Long> getOnlineUserIds() {
        return Collections.unmodifiableSet(userSessions.keySet());
    }
}
