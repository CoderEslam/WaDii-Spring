package com.doubleclick.wadii.notification;

import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Writes inbox entries in the caller's transaction and pushes them only after that transaction commits. */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final PushGateway push;
    private final Clock clock;

    public record View(Long id, NotificationType type, String title, String body, String refType, Long refId,
                       boolean read, Instant createdAt) {
        static View of(Notification n) {
            return new View(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getRefType(), n.getRefId(),
                    n.getReadAt() != null, n.getCreatedAt());
        }
    }

    @Transactional
    public void notify(Long userId, NotificationType type, String title, String body, String refType, Long refId) {
        if (userId == null) return;
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body.length() > 1000 ? body.substring(0, 1000) : body);
        n.setRefType(refType);
        n.setRefId(refId);
        repository.save(n);
        Map<String, String> data = refId == null ? Map.of("type", type.name())
                : Map.of("type", type.name(), "refType", refType, "refId", String.valueOf(refId));
        Runnable send = () -> CompletableFuture.runAsync(() -> push.send(userId, title, n.getBody(), data));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<View> list(Long userId, boolean unreadOnly, Integer page, Integer size) {
        var pageable = PageResponse.pageable(page, size);
        var result = unreadOnly ? repository.findByUserIdAndReadAtIsNullOrderByIdDesc(userId, pageable)
                : repository.findByUserIdOrderByIdDesc(userId, pageable);
        return PageResponse.of(result, View::of);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        Notification n = repository.findByIdAndUserId(id, userId).orElseThrow(() -> BusinessException.of(ErrorCode.NOT_FOUND));
        if (n.getReadAt() == null) n.setReadAt(Instant.now(clock));
    }

    @Transactional
    public void markAllRead(Long userId) {
        repository.markAllRead(userId, Instant.now(clock));
    }
}
