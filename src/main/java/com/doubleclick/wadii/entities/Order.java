package com.doubleclick.wadii.entities;


import com.doubleclick.wadii.auth.model.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Entity
@Setter
@Getter
@Table(name = "orders")
@AllArgsConstructor
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String carModelYear;
    private String comment;
    private LocalDate date;
    private Double latitude;
    private Double longitude;

    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.PENDING;

    // Provider the customer selected directly (null = matched by services)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id")
    @JsonIgnore
    private Provider provider;

    // Winning provider, set when a response is accepted
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_provider_id")
    @JsonIgnore
    private Provider acceptedProvider;

    private Long acceptedResponseId;

    @JsonProperty("providerId")
    public Long getTargetProviderId() {
        return provider == null ? null : provider.getId();
    }

    @JsonProperty("acceptedProviderId")
    public Long getAcceptedProviderId() {
        return acceptedProvider == null ? null : acceptedProvider.getId();
    }


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
//    @JsonIgnore
    @JsonIgnoreProperties({"orders", "hibernateLazyInitializer", "handler"}) // Prevents recursive fetching
    private User user;

    @ManyToMany(mappedBy = "orders", fetch = FetchType.LAZY)
    @JsonIgnoreProperties({"orders", "providers", "hibernateLazyInitializer", "handler"})
    private List<Service> services;


    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties({"order", "hibernateLazyInitializer", "handler"})
    private List<SpareParts> spareParts;


    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Responses> responses;

    // Set by controllers when the caller may only see part of the responses (e.g. a provider sees only their own).
    @Transient
    private List<Responses> responsesView;

    @JsonIgnore
    public List<Responses> getResponses() {
        return responses;
    }

    @JsonProperty("responses")
    @JsonIgnoreProperties({"order", "hibernateLazyInitializer", "handler"})
    public List<Responses> getResponsesForJson() {
        return responsesView != null ? responsesView : responses;
    }

    @JsonIgnore
    public List<Responses> getResponsesView() {
        return responsesView;
    }

}
