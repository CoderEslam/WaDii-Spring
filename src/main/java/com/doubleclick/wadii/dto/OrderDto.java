package com.doubleclick.wadii.dto;


import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OrderDto {

    private Long id;
    private String carModelYear;
    private String comment;
    private LocalDate date;
    private Long userId;
    private Double latitude;
    private Double longitude;
    private List<Long> servicesIds;
    private List<SparePartsDto> spareParts;

    public boolean isNotEmpty() {
        return carModelYear != null && !carModelYear.trim().isEmpty()
                && comment != null && !comment.trim().isEmpty()
                && date != null
                && userId != null
                && !servicesIds.isEmpty()
                && !spareParts.isEmpty();
    }

}
