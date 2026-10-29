package com.doubleclick.wadii.notification;

import java.time.Instant;

public record NotificationView(Long id, NotificationType type, String title, String body, Long orderId, String data,
                               boolean read, Instant readAt, Instant createdAt) {

    public static NotificationView of(Notification n) {
        return new NotificationView(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getOrderId(), n.getData(),
                n.getReadAt() != null, n.getReadAt(), n.getCreatedAt());
    }
}
