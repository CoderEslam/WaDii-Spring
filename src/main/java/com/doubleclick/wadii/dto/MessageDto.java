package com.doubleclick.wadii.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MessageDto {
    private Long id;
    private String text;
    private String type;
    private Long toUserId;

    public boolean isNotEmpty() {
        return text != null && !text.trim().isEmpty()
                && type != null && !type.trim().isEmpty()
                && toUserId != null;
    }
}