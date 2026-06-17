package com.doubleclick.wadii.auth.model;

import com.doubleclick.wadii.entities.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "users")
@AllArgsConstructor
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String firstName;
    private String lastName;
    @Column(unique = true, nullable = false)
    private String email;
    private String password;
    private String token;
    private String fcmToken;
    private String image;
    private String backgroundImage;
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;


    @OneToMany
    @JsonIgnore
    private List<Rate> rates;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties({"user", "provider"})
    private List<Follower> following; // <-- users following providers

    @OneToOne
    @JsonIgnoreProperties({"user", "hibernateLazyInitializer"}) // Prevents recursive fetching
    private Provider provider;


    public User(Provider provider) {
        this.provider = provider;
    }

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties({"user", "hibernateLazyInitializer", "handler"}) // Prevents recursive fetching
    private List<Order> orders;

    @ManyToOne
    @JoinColumn(name = "city_id")
    private City city;
}
