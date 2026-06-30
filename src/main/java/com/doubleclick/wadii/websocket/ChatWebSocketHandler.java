package com.doubleclick.wadii.websocket;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.repository.UserRepository;
import com.doubleclick.wadii.dto.ChatMessagePayload;
import com.doubleclick.wadii.entities.ChatContact;
import com.doubleclick.wadii.entities.Message;
import com.doubleclick.wadii.repository.ChatContactRepository;
import com.doubleclick.wadii.repository.MessageRepository;
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
    // userId → open WebSocketSession (one session per user; last one wins on reconnect)
    private final ConcurrentHashMap<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = userId(session);
        if (userId == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        sessions.put(userId, session);
        presenceRegistry.connect(userId, session.getId());
        broadcastPresence(userId, true);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = userId(session);
        if (userId == null) return;
        sessions.remove(userId, session);
        presenceRegistry.disconnect(session.getId());
        if (!presenceRegistry.isOnline(userId)) {
            broadcastPresence(userId, false);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable ex) {
        Long userId = userId(session);
        if (userId != null) {
            sessions.remove(userId, session);
            presenceRegistry.disconnect(session.getId());
        }
    }

    // ── Incoming message ──────────────────────────────────────────────────────

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage raw) throws Exception {
        Long fromUserId = userId(session);
        if (fromUserId == null) return;
        ChatMessagePayload payload = objectMapper.readValue(raw.getPayload(), ChatMessagePayload.class);
        Optional<User> fromOpt = userRepository.findById(fromUserId);
        Optional<User> toOpt = userRepository.findById(payload.getToUserId());
        if (fromOpt.isEmpty() || toOpt.isEmpty()) return;
        System.out.println(TAG + " 1 " + payload);
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
        System.out.println(TAG + " 2 "  + payload.getToUserId() + " = " + json);
        System.out.println(TAG + " 3 " + fromUserId + " = " +  json);
        send(payload.getToUserId(), json);
        send(fromUserId, json);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void send(Long userId, String json) {
        WebSocketSession s = sessions.get(userId);
        if (s != null && s.isOpen()) {
            try {
                System.out.println("");
                s.sendMessage(new TextMessage(json));
            } catch (IOException ignored) {
                System.out.println(ignored.getMessage());
            }
        }
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
        sessions.values().forEach(s -> {
            if (s.isOpen()) try {
                s.sendMessage(msg);
            } catch (IOException ignored) {
            }
        });
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
