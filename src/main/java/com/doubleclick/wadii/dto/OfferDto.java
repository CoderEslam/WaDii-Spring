package com.doubleclick.wadii.dto;


import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OfferDto {
    private Long id;
    private String title;
    private String description;
    private LocalDate endDate;
    private Long providerId;
    private List<Long> serviceIds;

    public boolean isNotEmpty() {
        return title != null && !title.trim().isEmpty()
                && description != null && !description.trim().isEmpty()
                && endDate != null
                && providerId != null;
    }
}
