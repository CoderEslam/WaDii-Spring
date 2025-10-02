package com.doubleclick.wadii.notification;

import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class Notification {
    private String title;
    private JsonObject message;
    private String type;
    private String status;
    private Long userId;
}
