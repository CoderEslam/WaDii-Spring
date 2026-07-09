package com.doubleclick.wadii.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LiveKitTokenRequest {
    private String roomName;
    private String identity;
    private String participantName;

    @Override
    public String toString() {
        return "LiveKitTokenRequest{" +
                "roomName='" + roomName + '\'' +
                ", identity='" + identity + '\'' +
                ", participantName='" + participantName + '\'' +
                '}';
    }
}
