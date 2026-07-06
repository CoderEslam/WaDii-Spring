package com.doubleclick.wadii.service;

import com.doubleclick.wadii.dto.CallSignal;
import com.doubleclick.wadii.notification.NotificationService;
import com.doubleclick.wadii.websocket.ChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;

@RequiredArgsConstructor
@Service
public class CallSignalingService {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final NotificationService notificationService;

    public void reject(CallSignal signal) {
        boolean delivered;
        try {
            delivered = chatWebSocketHandler.sendCallSignal("CALL_REJECT", signal);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (!delivered) {
            notificationService.sendCallSignalNotification(signal.getToUserId(), "CALL_REJECT", signal);
        }
    }
}
