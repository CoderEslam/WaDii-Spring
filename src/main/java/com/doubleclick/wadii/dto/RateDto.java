package com.doubleclick.wadii.dto;


import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class RateDto {

    private Long id;
    private String comment;
    private double rate;
    private Long providerId;
    private Long userId;

    public Boolean isNotEmpty() {
        return !comment.isEmpty() &&
                rate != 0.0 &&
                providerId != null &&
                userId != null;
    }
}

