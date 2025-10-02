package com.doubleclick.wadii.notification;

import com.doubleclick.wadii.auth.repository.UserRepository;
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
    private final String PROJECT_ID = "doctoronline-bfdc4";
    private final String BASE_URL = "https://fcm.googleapis.com";
    private final String FCM_SEND_ENDPOINT = BASE_URL + "/v1/projects/" + PROJECT_ID + "/messages:send";

    private String getAccessToken() {
        try {
            FileInputStream fileInputStream = new FileInputStream("src/main/doctor.json");
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
        String bearerToken = "Bearer " + getAccessToken();  // Replace with your actual token
        // Create headers and set the Authorization Bearer token
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);  // Adds "Authorization: Bearer <token>"
        headers.set("Content-Type", "application/json");
        headers.set("Accept", "application/json");
        // Create HTTP request entity
        JsonObject jsonObject = new JsonObject();
        JsonObject jsonObjectMessage = new JsonObject();
//        String deviceToken = userRepository.findById(notification.getUserId()).get().getFcmToken();
        JsonObject body = new JsonObject();
        body.addProperty("title", "DoctorOnline");
        body.addProperty("body", new JsonObjectConverter().convertToDatabaseColumn(notification.getMessage()));
        jsonObjectMessage.addProperty("token", "deviceToken");
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
                "deviceToken", "DoctorOnline", body
        );
        HttpEntity<String> entity = new HttpEntity<>(jsonObject.toString(), headers);
        // Make API request with headers
        ResponseEntity<String> response = restTemplate.exchange(FCM_SEND_ENDPOINT, HttpMethod.POST, entity, String.class);
        return response.getBody();
    }

}
