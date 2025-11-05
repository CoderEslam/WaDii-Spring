package com.doubleclick.wadii.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@Setter
@Getter
@Table(name = "services")
@AllArgsConstructor
@NoArgsConstructor
public class Service {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true, nullable = false)
    private String name;

    @ManyToMany
    @JoinTable(
            name = "service_providers",
            joinColumns = @JoinColumn(name = "service_id"),
            inverseJoinColumns = @JoinColumn(name = "provider_id")
    )
    @JsonIgnoreProperties("services") // Prevents recursive fetching
    private List<Provider> providers;

    @ManyToMany
    @JoinTable(
            name = "services_orders",
            joinColumns = @JoinColumn(name = "service_id"),
            inverseJoinColumns = @JoinColumn(name = "order_id")
    )
//    @JsonIgnoreProperties("services") // Prevents recursive fetching
    @JsonIgnore // ❌ Never include orders in Service responses
    private List<Order> orders;


}
