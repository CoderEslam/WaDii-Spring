package com.doubleclick.wadii.dto;



import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CarTypesDto {
    private Long id;
    private String name;

    public boolean isNotEmpty() {
        return name != null && !name.trim().isEmpty();
    }
}
