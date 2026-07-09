package com.doubleclick.wadii.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LiveKitTokenResponse {
    private String url;
    private String token;

    @Override
    public String toString() {
        return "LiveKitTokenResponse{" +
                "url='" + url + '\'' +
                ", token='" + token + '\'' +
                '}';
    }
}
