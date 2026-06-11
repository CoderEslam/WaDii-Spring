package com.doubleclick.wadii.entities;

import com.doubleclick.wadii.auth.model.User;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "provider_requests")
@AllArgsConstructor
@NoArgsConstructor
public class ProviderRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String frontIdImage;

    private String backIdImage;

    private String address;

    private String phoneNumber;

    @ManyToMany
    @JoinTable(
            name = "provider_request_services",
            joinColumns = @JoinColumn(name = "request_id"),
            inverseJoinColumns = @JoinColumn(name = "service_id")
    )
    @JsonIgnoreProperties({"providers", "orders"})
    private List<Service> services;

    @ElementCollection
    @CollectionTable(name = "provider_request_links", joinColumns = @JoinColumn(name = "request_id"))
    @Column(name = "link")
    private List<String> links;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"provider", "orders", "following", "rates", "hibernateLazyInitializer"})
    private User user;

    private LocalDateTime requestedAt = LocalDateTime.now();
}