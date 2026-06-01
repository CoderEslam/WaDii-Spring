package com.doubleclick.wadii.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProvinceDto {
    private Long id;
    private String name;
    private Long countryId;

    public boolean isNotEmpty() {
        return name != null && !name.trim().isEmpty() && countryId != null;
    }
}
