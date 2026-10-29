package com.doubleclick.wadii.notification;

import com.doubleclick.wadii.common.config.WadiiProperties;
import com.doubleclick.wadii.identity.UserRepository;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** FCM HTTP v1 sender. Disabled (with a warning) when no credentials file is configured. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushGateway {

    private final WadiiProperties properties;
    private final UserRepository userRepository;
    private final RestClient restClient = RestClient.create();
    private volatile GoogleCredentials credentials;
    private volatile boolean unavailable;

    /** Visible notification. */
    public void send(Long userId, String title, String body, String type, Long orderId) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", type);
        if (orderId != null) data.put("orderId", String.valueOf(orderId));
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("notification", Map.of("title", title, "body", body));
        message.put("data", data);
        deliver(userId, message);
    }

    /** Data-only message (call signaling) handled by the app in the background. */
    public void sendData(Long userId, Map<String, String> data) {
        deliver(userId, new LinkedHashMap<>(Map.of("data", data)));
    }

    private void deliver(Long userId, Map<String, Object> message) {
        if (!properties.getFcm().isEnabled()) return;
        try {
            String token = userRepository.findById(userId).map(u -> u.getFcmToken()).orElse(null);
            if (token == null || token.isBlank()) return;
            String accessToken = accessToken();
            if (accessToken == null) return;
            message.put("token", token);
            restClient.post()
                    .uri("https://fcm.googleapis.com/v1/projects/{p}/messages:send", properties.getFcm().getProjectId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + accessToken)
                    .body(Map.of("message", message))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("FCM push to user {} failed: {}", userId, e.getMessage());
        }
    }

    private String accessToken() throws Exception {
        if (unavailable) return null;
        if (credentials == null) {
            synchronized (this) {
                if (credentials == null) {
                    Path path = Path.of(properties.getFcm().getCredentialsPath());
                    if (!Files.isReadable(path)) {
                        log.warn("FCM credentials not found at {}; push notifications are disabled", path);
                        unavailable = true;
                        return null;
                    }
                    try (FileInputStream in = new FileInputStream(path.toFile())) {
                        credentials = GoogleCredentials.fromStream(in)
                                .createScoped(List.of("https://www.googleapis.com/auth/firebase.messaging"));
                    }
                }
            }
        }
        credentials.refreshIfExpired();
        return credentials.getAccessToken().getTokenValue();
    }
}
