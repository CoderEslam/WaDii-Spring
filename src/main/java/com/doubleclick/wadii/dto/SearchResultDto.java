package com.doubleclick.wadii.dto;

import com.doubleclick.wadii.entities.Branch;
import com.doubleclick.wadii.entities.Offer;
import com.doubleclick.wadii.entities.Provider;
import com.doubleclick.wadii.entities.Service;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SearchResultDto {
    private List<Offer> offers;
    private List<Service> services;
    private List<Provider> providers;
    private List<Branch> branches;
}
