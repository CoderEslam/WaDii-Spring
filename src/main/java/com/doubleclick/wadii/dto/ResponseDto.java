package com.doubleclick.wadii.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ResponseDto {

    private Long id;
    private Long providerId;
    private Long orderId;
    private String comment;
    private Double latitude;
    private Double longitude;
    private List<SparePartsPriceDto> sparePartsPrice;

    public boolean isNotEmpty() {
        return comment != null && !comment.trim().isEmpty();
    }
}
