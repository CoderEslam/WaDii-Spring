package com.doubleclick.wadii.dto;

import java.util.ArrayList;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UpdateProviderDto {
    public String firstName;
    public String lastName;
    public String phone;
    public String email;
    public ArrayList<Long> serviceIds;
    public ArrayList<Branch> branches;
    public ArrayList<Link> links;
    public ArrayList<Offer> offers;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Branch {
        public int id;
        public String name;
        public String address;
        public ArrayList<WorkTime> workTimes;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class WorkTime {
        public int id;
        public String day;
        public String startTime;
        public String closeTime;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Link {
        public int id;
        public String link;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Offer {
        public int id;
        public String title;
        public String description;
        public String endDate;
    }

}
