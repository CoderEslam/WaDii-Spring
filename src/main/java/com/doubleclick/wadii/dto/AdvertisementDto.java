package com.doubleclick.wadii.dto;

import com.doubleclick.wadii.entities.AdvertisementStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AdvertisementDto {
    private Long id;
    private String title;
    private String description;
    private String imageUrl;
    private String targetUrl;
    private String advertiserName;
    private AdvertisementStatus status;
    private Integer priority;
    private LocalDateTime startDate;
    private LocalDateTime endDate;

}
