package com.doubleclick.wadii.websocket;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.repository.UserRepository;
import com.doubleclick.wadii.dto.ChatMessagePayload;
import com.doubleclick.wadii.dto.ReadReceiptPayload;
import com.doubleclick.wadii.dto.TypingPayload;
import com.doubleclick.wadii.entities.ChatContact;
import com.doubleclick.wadii.entities.Message;
import com.doubleclick.wadii.repository.ChatContactRepository;
import com.doubleclick.wadii.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Handles real-time chat messages sent over the STOMP WebSocket connection.
 *
 * Connection: ws(s)://host/ws-native?token=JWT  (plain WS for mobile)
 *             ws(s)://host/ws?token=JWT          (SockJS for web)
 *
 * Client subscriptions:
 *   /user/queue/messages       — incoming chat messages
 *   /user/queue/typing         — typing indicators from contacts
 *   /user/queue/read-receipts  — read-receipt confirmations
 *
 * Client sends (STOMP SEND frame):
 *   /app/chat.send   — ChatMessagePayload  { toUserId, text, type }
 *   /app/chat.typing — TypingPayload       { toUserId, typing }
 *   /app/chat.read   — ReadReceiptPayload  { messageId }
 */
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ChatContactRepository chatContactRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // ── Send a chat message ───────────────────────────────────────────────────

    @MessageMapping("/chat.send")
    public void sendMessage(Principal principal, @Payload ChatMessagePayload payload) {
        Long fromUserId = Long.parseLong(principal.getName());

        Optional<User> fromUserOpt = userRepository.findById(fromUserId);
        Optional<User> toUserOpt = userRepository.findById(payload.getToUserId());
        if (fromUserOpt.isEmpty() || toUserOpt.isEmpty()) return;

        User fromUser = fromUserOpt.get();
        User toUser = toUserOpt.get();
        String type = (payload.getType() != null && !payload.getType().isBlank()) ? payload.getType() : "text";

        Message message = new Message();
        message.setText(payload.getText());
        message.setType(type);
        message.setFromUser(fromUser);
        message.setToUser(toUser);
        message = messageRepository.save(message);

        upsertChatContact(fromUser, toUser, payload.getText(), type);
        upsertChatContact(toUser, fromUser, payload.getText(), type);

        // Push to both parties so sender sees the saved message (with id/timestamp).
        messagingTemplate.convertAndSendToUser(String.valueOf(toUser.getId()), "/queue/messages", message);
        messagingTemplate.convertAndSendToUser(String.valueOf(fromUserId), "/queue/messages", message);
    }

    // ── Typing indicator ──────────────────────────────────────────────────────

    @MessageMapping("/chat.typing")
    public void typingIndicator(Principal principal, @Payload TypingPayload payload) {
        Long fromUserId = Long.parseLong(principal.getName());
        Map<String, Object> event = Map.of(
                "fromUserId", fromUserId,
                "typing", payload.isTyping()
        );
        messagingTemplate.convertAndSendToUser(String.valueOf(payload.getToUserId()), "/queue/typing", event);
    }

    // ── Read receipt ──────────────────────────────────────────────────────────

    @MessageMapping("/chat.read")
    public void markAsRead(Principal principal, @Payload ReadReceiptPayload payload) {
        Long readerId = Long.parseLong(principal.getName());

        // Verify the message exists, is addressed to this user, and is unread.
        Optional<Long> senderIdOpt = messageRepository.findSenderIdForUnreadMessage(payload.getMessageId(), readerId);
        if (senderIdOpt.isEmpty()) return;

        messageRepository.markAsRead(payload.getMessageId(), readerId);

        Map<String, Object> receipt = Map.of(
                "messageId", payload.getMessageId(),
                "readBy", readerId
        );
        messagingTemplate.convertAndSendToUser(String.valueOf(senderIdOpt.get()), "/queue/read-receipts", receipt);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void upsertChatContact(User user, User contact, String lastMessage, String messageType) {
        ChatContact chatContact = chatContactRepository
                .findByUserIdAndContactId(user.getId(), contact.getId())
                .orElseGet(() -> {
                    ChatContact c = new ChatContact();
                    c.setUser(user);
                    c.setContact(contact);
                    return c;
                });
        chatContact.setLastMessageAt(LocalDateTime.now());
        chatContact.setLastMessage(lastMessage);
        chatContact.setMessageType(messageType);
        chatContactRepository.save(chatContact);
    }
}
