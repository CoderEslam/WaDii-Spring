package com.doubleclick.wadii.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CommonSparePartsDto {

    private Long id;
    private String name;

    public boolean isNotEmpty() {
        return name != null && !name.trim().isEmpty();
    }

}
