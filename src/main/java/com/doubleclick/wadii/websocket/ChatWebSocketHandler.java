package com.doubleclick.wadii.websocket;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.repository.UserRepository;
import com.doubleclick.wadii.dto.CallSignal;
import com.doubleclick.wadii.dto.ChatMessagePayload;
import com.doubleclick.wadii.entities.ChatContact;
import com.doubleclick.wadii.entities.Message;
import com.doubleclick.wadii.repository.ChatContactRepository;
import com.doubleclick.wadii.repository.MessageRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ChatContactRepository chatContactRepository;
    private final PresenceRegistry presenceRegistry;
    private final ObjectMapper objectMapper;
    private static final String TAG = "ChatWebSocketHandler";
    private static final Set<String> CALL_EVENTS = Set.of("CALL_INVITE", "CALL_ACCEPT", "CALL_REJECT", "CALL_END");
    // userId → open WebSocketSessions (a user may have multiple concurrent sockets, e.g. chat + calls)
    private final ConcurrentHashMap<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = userId(session);
        if (userId == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        sessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
        presenceRegistry.connect(userId, session.getId());
        broadcastPresence(userId, true);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = userId(session);
        if (userId == null) return;
        removeSession(userId, session);
        presenceRegistry.disconnect(session.getId());
        if (!presenceRegistry.isOnline(userId)) {
            broadcastPresence(userId, false);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable ex) {
        Long userId = userId(session);
        if (userId != null) {
            removeSession(userId, session);
            presenceRegistry.disconnect(session.getId());
        }
    }

    // ── Incoming message ──────────────────────────────────────────────────────

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage raw) throws Exception {
        Long fromUserId = userId(session);
        if (fromUserId == null) return;
        JsonNode root = objectMapper.readTree(raw.getPayload());
        String event = root.hasNonNull("event") ? root.get("event").asText() : null;
        if (event != null && CALL_EVENTS.contains(event)) {
            handleCallSignal(fromUserId, event, root.get("data"));
            return;
        }
        ChatMessagePayload payload = objectMapper.treeToValue(root, ChatMessagePayload.class);
        Optional<User> fromOpt = userRepository.findById(fromUserId);
        Optional<User> toOpt = userRepository.findById(payload.getToUserId());
        if (fromOpt.isEmpty() || toOpt.isEmpty()) return;
        User from = fromOpt.get();
        User to = toOpt.get();
        String type = (payload.getType() != null && !payload.getType().isBlank())
                ? payload.getType().toLowerCase() : "text";

        Message msg = new Message();
        msg.setText(payload.getText());
        msg.setType(type);
        msg.setFromUser(from);
        msg.setToUser(to);
        msg = messageRepository.save(msg);

        upsertChatContact(from, to, payload.getText(), type);
        upsertChatContact(to, from, payload.getText(), type);

        String json = objectMapper.writeValueAsString(
                Map.of("event", "MESSAGE", "data", msg));

        // Deliver to recipient (if online) and echo to sender
        send(payload.getToUserId(), json);
        send(fromUserId, json);
    }

    private void handleCallSignal(Long fromUserId, String event, JsonNode dataNode) throws IOException {
        if (dataNode == null || dataNode.isNull()) return;
        CallSignal signal = objectMapper.treeToValue(dataNode, CallSignal.class);
        // never trust the client-supplied sender id — it must match the authenticated socket
        signal.setFromUserId(fromUserId);
        if (signal.getToUserId() == null) return;

        sendCallSignal(event, signal);
    }

    /**
     * Pushes a call-signaling event (CALL_INVITE/CALL_ACCEPT/CALL_REJECT/CALL_END) to the target
     * user's open socket(s). Also usable from REST controllers, e.g. as a fallback path for
     * clients that aren't holding the chat socket open. Returns whether the recipient had an
     * open session to deliver to.
     */
    public boolean sendCallSignal(String event, CallSignal signal) throws IOException {
        String json = objectMapper.writeValueAsString(Map.of("event", event, "data", signal));
        return send(signal.getToUserId(), json);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void removeSession(Long userId, WebSocketSession session) {
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null) return;
        userSessions.remove(session);
        sessions.computeIfPresent(userId, (k, v) -> v.isEmpty() ? null : v);
    }

    private boolean send(Long userId, String json) {
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null) return false;
        TextMessage msg = new TextMessage(json);
        boolean delivered = false;
        for (WebSocketSession s : userSessions) {
            if (s.isOpen()) {
                try {
                    s.sendMessage(msg);
                    delivered = true;
                } catch (IOException ignored) {
                    System.out.println(ignored.getMessage());
                }
            }
        }
        return delivered;
    }

    private void broadcastPresence(Long userId, boolean online) {
        String json;
        try {
            json = objectMapper.writeValueAsString(
                    Map.of("event", "PRESENCE", "data", Map.of("userId", userId, "online", online)));
        } catch (IOException e) {
            return;
        }
        TextMessage msg = new TextMessage(json);
        sessions.values().forEach(userSessions -> userSessions.forEach(s -> {
            if (s.isOpen()) try {
                s.sendMessage(msg);
            } catch (IOException ignored) {
            }
        }));
    }

    private Long userId(WebSocketSession session) {
        Principal p = session.getPrincipal();
        if (p == null) return null;
        try {
            return Long.parseLong(p.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void upsertChatContact(User user, User contact, String lastMessage, String messageType) {
        ChatContact cc = chatContactRepository
                .findByUserIdAndContactId(user.getId(), contact.getId())
                .orElseGet(() -> {
                    ChatContact c = new ChatContact();
                    c.setUser(user);
                    c.setContact(contact);
                    return c;
                });
        cc.setLastMessageAt(LocalDateTime.now());
        cc.setLastMessage(lastMessage);
        cc.setMessageType(messageType);
        chatContactRepository.save(cc);
    }
}
