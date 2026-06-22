package com.doubleclick.wadii.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserNotificationDto {
    private Long id;
    private String title;
    private String body;
    private String type;
    private Long userId;

    public boolean isNotEmpty() {
        return title != null && !title.trim().isEmpty()
                && body != null && !body.trim().isEmpty()
                && userId != null;
    }
}
