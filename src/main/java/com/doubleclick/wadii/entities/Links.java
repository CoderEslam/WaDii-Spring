package com.doubleclick.wadii.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Setter
@Getter
@Table(name = "links")
@AllArgsConstructor
@NoArgsConstructor
public class Links {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String link;


    @ManyToOne
    @JoinColumn(name = "provider_id", nullable = false)
    @JsonIgnore
    private Provider provider;

}
