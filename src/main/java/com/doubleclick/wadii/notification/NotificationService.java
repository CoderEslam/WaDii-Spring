package com.doubleclick.wadii.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Persists the notification in the caller's transaction, then — only after commit — delivers it over
 * WebSocket ({@code /user/queue/notifications}) and FCM. Delivery failures never affect the business operation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final PushGateway pushGateway;
    private final SimpMessagingTemplate messagingTemplate;
    private final ExecutorService deliveryExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "notification-delivery");
        t.setDaemon(true); // never blocks JVM shutdown
        return t;
    });

    public void notify(Long userId, NotificationType type, String title, String body, Long orderId) {
        notify(userId, type, title, body, orderId, null);
    }

    public void notify(Long userId, NotificationType type, String title, String body, Long orderId, Map<String, String> data) {
        if (userId == null) return;
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setOrderId(orderId);
        n.setData(data == null || data.isEmpty() ? null : data.toString());
        Notification saved = repository.save(n);
        NotificationView view = NotificationView.of(saved);
        Runnable deliver = () -> deliveryExecutor.submit(() -> {
            try {
                messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/notifications", view);
            } catch (Exception e) {
                log.debug("WebSocket delivery of notification {} failed: {}", saved.getId(), e.getMessage());
            }
            pushGateway.send(userId, title, body, type.name(), orderId);
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deliver.run();
                }
            });
        } else {
            deliver.run();
        }
    }
}
