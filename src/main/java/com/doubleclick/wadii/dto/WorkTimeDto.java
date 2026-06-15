package com.doubleclick.wadii.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class WorkTimeDto {

    private Long id;
    private String startTime;
    private String closeTime;
    private String day;
    private Long branchId;

    public boolean isNotEmpty() {
        return startTime != null && !startTime.trim().isEmpty()
                && closeTime != null && !closeTime.trim().isEmpty()
                && day != null && !day.trim().isEmpty();
    }
}
