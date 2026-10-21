package com.doubleclick.wadii.service;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.entities.UserNotification;
import com.doubleclick.wadii.notification.Notification;
import com.doubleclick.wadii.notification.NotificationService;
import com.doubleclick.wadii.repository.UserNotificationRepository;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Stores the notification in the inbox first, then sends the FCM push.
 * The push is sent after the surrounding transaction commits and a push failure never
 * affects the business operation: the inbox row stays.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserNotifier {

    private final UserNotificationRepository userNotificationRepository;
    private final NotificationService notificationService;

    public void notify(User user, String title, String body, String type, Long orderId) {
        if (user == null) return;
        UserNotification inbox = new UserNotification();
        inbox.setTitle(title);
        inbox.setBody(body);
        inbox.setType(type);
        inbox.setUser(user);
        inbox.setOrderId(orderId);
        userNotificationRepository.save(inbox);

        if (user.getFcmToken() == null || user.getFcmToken().isBlank()) return;
        Long userId = user.getId();
        Runnable push = () -> {
            try {
                JsonObject message = new JsonObject();
                message.addProperty("body", body);
                notificationService.sendNotification(new Notification(title, message, type, "SENT", userId));
            } catch (Exception e) {
                log.warn("FCM push '{}' to user {} failed: {}", type, userId, e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    push.run();
                }
            });
        } else {
            push.run();
        }
    }
}
