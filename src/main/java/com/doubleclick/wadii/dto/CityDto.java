package com.doubleclick.wadii.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CityDto {
    private Long id;
    private String name;
    private Long provinceId;

    public boolean isNotEmpty() {
        return name != null && !name.trim().isEmpty() && provinceId != null;
    }
}
