package com.doubleclick.wadii.entities;

import com.doubleclick.wadii.auth.model.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Setter
@Getter
@Table(name = "rates", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "provider_id", "order_id"}))
@AllArgsConstructor
@NoArgsConstructor
public class Rate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String comment;
    private double rate;


    @ManyToOne
    @JoinColumn(name = "provider_id", nullable = false)
    @JsonIgnoreProperties({"rates", "responses", "workTimes", "followers", "offers", "links", "services", "hibernateLazyInitializer", "handler"})
    private Provider provider;


    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"rates", "orders", "following", "provider", "email", "phone", "hibernateLazyInitializer", "handler"})
    private User user;

    // The completed order this rating is for (null for ratings created before orders were required)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    @JsonIgnore
    private Order order;

    @Override
    public String toString() {
        return "Rate{" +
                "id=" + id +
                ", comment='" + comment + '\'' +
                ", rate=" + rate +
                ", provider=" + provider +
                ", user=" + user +
                '}';
    }
//    @ManyToOne
//    @JoinColumn(name = "province_id", nullable = false)
//    @JsonIgnoreProperties("cities") // Prevents recursive fetching
//    private Province province;

}
