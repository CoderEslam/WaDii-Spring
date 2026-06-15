package com.doubleclick.wadii.dto;

import com.doubleclick.wadii.auth.model.User;
import com.doubleclick.wadii.entities.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ProviderDto {


    private Long id;
    private Double rate;
    private Long followersCount;
    private List<Branch> branches;
    private List<Service> services;
    private List<Rate> rates;
    private List<Links> links;
    private List<Offer> offers;
    private List<Follower> followers;
    private Long userId;
    private List<Responses> responses;

}
