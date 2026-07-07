package com.doubleclick.wadii.notification;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.auth.repository.UserRepository;
import com.doubleclick.wadii.dto.CallSignal;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.FileInputStream;

@RequiredArgsConstructor
@Service
public class NotificationService {

    private final UserRepository userRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final String PROJECT_ID = "wadii-kmp";
    private final String BASE_URL = "https://fcm.googleapis.com";
    private final String FCM_SEND_ENDPOINT = BASE_URL + "/v1/projects/" + PROJECT_ID + "/messages:send";

    private String getAccessToken() {
        try {
            FileInputStream fileInputStream = new FileInputStream("src/main/wadii.json");
            GoogleCredentials googleCredentials = GoogleCredentials.fromStream(fileInputStream)
                    .createScoped(
                            "https://www.googleapis.com/auth/firebase.messaging",
                            "https://www.googleapis.com/auth/cloud-platform"
                    );
            googleCredentials.refreshIfExpired();
            return googleCredentials.getAccessToken().getTokenValue();
        } catch (Exception e) {
            System.out.println("NotificationServiceImpl" + e.getMessage());
            return "";
        }
    }


    public String sendNotification(Notification notification) {
        // Create headers and set the Authorization Bearer token
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());  // Adds "Authorization: Bearer <token>"
        headers.set("Content-Type", "application/json");
        headers.set("Accept", "application/json");
        // Create HTTP request entity
        JsonObject jsonObject = new JsonObject();
        JsonObject jsonObjectMessage = new JsonObject();
        String deviceToken = userRepository.findById(notification.getUserId()).get().getFcmToken();
        JsonObject body = new JsonObject();
        body.addProperty("title", "WaDii");
        body.addProperty("body", new JsonObjectConverter().convertToDatabaseColumn(notification.getMessage()));
        jsonObjectMessage.addProperty("token", deviceToken);
        jsonObjectMessage.add("notification", body);
        jsonObject.add("message", jsonObjectMessage);
        String jsonBody = String.format(
                "{"
                        + "\"message\":{"
                        + "  \"token\":\"%s\","
                        + "  \"notification\":{"
                        + "    \"title\":\"%s\","
                        + "    \"body\":\"%s\""
                        + "  },"
                        + "  \"data\":{"
                        + "    \"click_action\":\"FLUTTER_NOTIFICATION_CLICK\""
                        + "  }"
                        + "}"
                        + "}",
                deviceToken, "WaDii", body
        );
        HttpEntity<String> entity = new HttpEntity<>(jsonObject.toString(), headers);
        // Make API request with headers
        ResponseEntity<String> response = restTemplate.exchange(FCM_SEND_ENDPOINT, HttpMethod.POST, entity, String.class);
        return response.getBody();
    }

    /**
     * Data-only push used as a fallback when a call-signaling event (e.g. CALL_REJECT) can't be
     * delivered over an open websocket session. No visible "notification" block is sent since the
     * client handles these events programmatically rather than showing a system tray entry.
     */
    public void sendCallSignalNotification(Long userId, String event, CallSignal signal) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getFcmToken() == null || user.getFcmToken().isBlank()) return;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());
        headers.set("Content-Type", "application/json");
        headers.set("Accept", "application/json");

        JsonObject data = new JsonObject();
        data.addProperty("event", event);
        data.addProperty("channelName", signal.getChannelName());
        if (signal.getCallType() != null) data.addProperty("callType", signal.getCallType());
        if (signal.getFromUserId() != null) data.addProperty("fromUserId", String.valueOf(signal.getFromUserId()));
        if (signal.getFromUserName() != null) data.addProperty("fromUserName", signal.getFromUserName());
        if (signal.getFromUserImage() != null) data.addProperty("fromUserImage", signal.getFromUserImage());

        JsonObject message = new JsonObject();
        message.addProperty("token", user.getFcmToken());
        message.add("data", data);

        JsonObject body = new JsonObject();
        body.add("message", message);

        HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);
        restTemplate.exchange(FCM_SEND_ENDPOINT, HttpMethod.POST, entity, String.class);
    }

}
