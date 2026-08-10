package com.doubleclick.wadii.entities;

import com.doubleclick.wadii.auth.model.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collections;
import java.util.List;

@Entity
@Setter
@Getter
@Table(name = "providers")
@AllArgsConstructor
@NoArgsConstructor
public class Provider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Double rate;
    @Column(nullable = false)
    private Long followersCount;
    @Column(nullable = false)
    private String name;


    @OneToMany(mappedBy = "provider", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties({"provider"})
    private List<Branch> branches;

    @ManyToMany(mappedBy = "providers")
    @JsonIgnoreProperties({"providers", "orders"}) // Hide orders from services inside provider
    private List<Service> services;

    @OneToMany(mappedBy = "provider", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties({"provider", "user"})
    private List<Rate> rates;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "provider", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Links> links;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "provider", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("provider") // Prevents recursive fetching
    private List<Offer> offers;

    @OneToMany(mappedBy = "provider", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("provider")
    @JsonIgnore
    private List<Follower> followers; // <-- providers followed by users

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"provider", "orders", "following", "rates", "hibernateLazyInitializer"})
    private User user;

    @OneToMany
    @JsonIgnore
    private List<Responses> responses;


//    public Provider(Long id) {
//        this.id = id;
//        this.rate = 0.0;
//        this.followersCount = 0L;
//        this.name = "";
//        this.branches = Collections.emptyList();
//        this.services = Collections.emptyList();
//        this.rates = Collections.emptyList();
//        this.links = Collections.emptyList();
//        this.offers = Collections.emptyList();
//        this.followers = Collections.emptyList();
//        this.user = new User(0L);
//        this.responses = Collections.emptyList();
//    }
}
