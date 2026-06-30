package com.doubleclick.wadii.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessagePayload {
    private Long toUserId;
    private String text;
    private String type; // "text", "image", "audio"

    @Override
    public String toString() {
        return "ChatMessagePayload{" +
                "toUserId=" + toUserId +
                ", text='" + text + '\'' +
                ", type='" + type + '\'' +
                '}';
    }
}
