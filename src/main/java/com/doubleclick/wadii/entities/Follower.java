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
@Table(name = "followers")
@AllArgsConstructor
@NoArgsConstructor
public class Follower {

    @EmbeddedId
    private FollowerId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId") // maps this field to userId in FollowerId
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"following", "provider", "orders", "rates"})
    @JsonIgnore
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("providerId") // maps this field to providerId in FollowerId
    @JoinColumn(name = "provider_id", nullable = false)
    @JsonIgnoreProperties({"followers", "workTimes", "services", "offers", "rates", "links"})
    @JsonIgnore
    private Provider provider;


}
