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
public class CreateProviderDto {

    // User account fields
    private String firstName;
    private String lastName;
    private String email;
    private String password;
    private String phone;
    private Long cityId;

    // Provider fields
    private String providerName;
    private Double rate;
    private List<Long> serviceIds;
    private List<Branch> branches;
    private List<Link> links;
    private List<Offer> offers;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Branch {
        private String name;
        private String address;
        private List<WorkTime> workTimes;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class WorkTime {
        private String day;
        private String startTime;
        private String closeTime;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Link {
        private String link;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Offer {
        private String title;
        private String description;
        private String endDate;
    }
}
