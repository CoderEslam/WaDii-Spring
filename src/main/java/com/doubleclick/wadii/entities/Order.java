package com.doubleclick.wadii.entities;


import com.doubleclick.wadii.auth.model.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
//    @JsonIgnoreProperties("orders") // Prevents recursive fetching
    private User user;

    @ManyToMany(mappedBy = "orders", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = {"orders", "providers"}) // Prevents recursive fetching
    private List<Service> services;


    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties("order") // Prevents recursive fetching
    private List<SpareParts> spareParts;


    @OneToMany
    private List<Responses> responses;

}
