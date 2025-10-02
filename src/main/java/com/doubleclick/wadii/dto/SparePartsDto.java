package com.doubleclick.wadii.dto;

import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class SparePartsDto {

    private Long id;
    private String sparePartName;

    public boolean isNotEmpty() {
        return sparePartName != null && !sparePartName.trim().isEmpty();
    }
}
