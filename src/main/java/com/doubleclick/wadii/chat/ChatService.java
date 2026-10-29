package com.doubleclick.wadii.chat;

import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.notification.PushGateway;
import com.doubleclick.wadii.storage.FileCategory;
import com.doubleclick.wadii.storage.FileStorageService;
import com.doubleclick.wadii.storage.StoredFile;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * Order chat. Messages are persisted; delivery is STOMP {@code /user/queue/messages} to both participants after
 * commit plus an FCM push to the recipient. Chat messages are not copied into the notification inbox.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationService conversationService;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final FileStorageService storage;
    private final SimpMessagingTemplate messaging;
    private final PushGateway pushGateway;
    private final Clock clock;

    public record ConversationView(Long id, Long orderId, Long customerId, Long providerId, Long providerUserId,
                                   Instant lastMessageAt, String lastMessagePreview, Instant createdAt) {
        static ConversationView of(Conversation c) {
            return new ConversationView(c.getId(), c.getOrderId(), c.getCustomerId(), c.getProviderId(), c.getProviderUserId(),
                    c.getLastMessageAt(), c.getLastMessagePreview(), c.getCreatedAt());
        }
    }

    public record MessageView(Long id, Long conversationId, Long senderId, MessageType type, String text, boolean hasFile,
                              String contentType, Instant createdAt, Instant readAt) {
        public static MessageView of(Message m) {
            return new MessageView(m.getId(), m.getConversationId(), m.getSenderId(), m.getType(), m.getText(),
                    m.getFileKey() != null, m.getContentType(), m.getCreatedAt(), m.getReadAt());
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<ConversationView> conversations(AuthUser me, Integer page, Integer size) {
        var pageable = PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "lastMessageAt"));
        return PageResponse.of(conversationRepository.findForUser(me.id(), pageable), ConversationView::of);
    }

    @Transactional(readOnly = true)
    public ConversationView forOrder(AuthUser me, Long orderId) {
        Conversation c = conversationRepository.findByOrderId(orderId).orElseThrow(() -> BusinessException.notFound(ErrorCode.CONVERSATION_NOT_FOUND));
        return ConversationView.of(conversationService.requireReader(me, c.getId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<MessageView> messages(AuthUser me, Long conversationId, Integer page, Integer size) {
        conversationService.requireReader(me, conversationId);
        var pageable = PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.of(messageRepository.findByConversationId(conversationId, pageable), MessageView::of);
    }

    @Transactional
    public MessageView sendText(AuthUser me, Long conversationId, String text) {
        if (text == null || text.isBlank()) throw BusinessException.validation("text must not be blank");
        if (text.length() > 4000) throw BusinessException.validation("text is too long");
        Conversation c = conversationService.requireParticipant(me, conversationId);
        Message m = new Message();
        m.setConversationId(c.getId());
        m.setSenderId(me.id());
        m.setType(MessageType.TEXT);
        m.setText(text.trim());
        return deliver(c, messageRepository.save(m), text.trim());
    }

    @Transactional
    public MessageView sendMedia(AuthUser me, Long conversationId, MultipartFile file, String caption) {
        Conversation c = conversationService.requireParticipant(me, conversationId);
        StoredFile stored = storage.store(file, FileCategory.CHAT_MEDIA);
        Message m = new Message();
        m.setConversationId(c.getId());
        m.setSenderId(me.id());
        m.setType(stored.contentType().startsWith("audio/") ? MessageType.AUDIO : MessageType.IMAGE);
        m.setText(caption == null || caption.isBlank() ? null : caption.trim());
        m.setFileKey(stored.key());
        m.setContentType(stored.contentType());
        return deliver(c, messageRepository.save(m), m.getType() == MessageType.AUDIO ? "Voice message" : "Photo");
    }

    @Transactional
    public int markRead(AuthUser me, Long conversationId) {
        Conversation c = conversationService.requireParticipant(me, conversationId);
        int updated = messageRepository.markRead(conversationId, me.id(), clock.instant());
        if (updated > 0) {
            afterCommit(() -> messaging.convertAndSendToUser(String.valueOf(c.otherParticipant(me.id())), "/queue/read-receipts",
                    Map.of("conversationId", conversationId, "readBy", me.id())));
        }
        return updated;
    }

    public void typing(AuthUser me, Long conversationId, boolean typing) {
        Conversation c = conversationService.requireParticipant(me, conversationId);
        messaging.convertAndSendToUser(String.valueOf(c.otherParticipant(me.id())), "/queue/typing",
                Map.of("conversationId", conversationId, "userId", me.id(), "typing", typing));
    }

    @Transactional(readOnly = true)
    public long unreadCount(AuthUser me) {
        return messageRepository.countUnread(me.id());
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> mediaFile(AuthUser me, Long messageId) {
        Message m = messageRepository.findById(messageId).orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND));
        conversationService.requireReader(me, m.getConversationId());
        if (m.getFileKey() == null) throw BusinessException.notFound(ErrorCode.NOT_FOUND);
        return storage.servePrivate(m.getFileKey(), m.getContentType());
    }

    private MessageView deliver(Conversation c, Message m, String preview) {
        c.setLastMessageAt(m.getCreatedAt());
        c.setLastMessagePreview(preview.length() > 200 ? preview.substring(0, 200) : preview);
        MessageView view = MessageView.of(m);
        Long recipient = c.otherParticipant(m.getSenderId());
        afterCommit(() -> {
            messaging.convertAndSendToUser(String.valueOf(recipient), "/queue/messages", view);
            messaging.convertAndSendToUser(String.valueOf(m.getSenderId()), "/queue/messages", view);
            pushGateway.send(recipient, "New message", c.getLastMessagePreview(), "NEW_MESSAGE", c.getOrderId());
        });
        return view;
    }

    private static void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    r.run();
                }
            });
        } else {
            r.run();
        }
    }
}
