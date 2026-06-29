package com.doubleclick.wadii.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Setter
@Getter
@Table(name = "cities")
@AllArgsConstructor
@NoArgsConstructor
public class City {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true, nullable = false)
    private String name;

    @ManyToOne
    @JoinColumn(name = "province_id")
    private Province province;

    public City(Long id) {
        this.id = id;
        this.name = "";
        this.province = new Province(0L);
    }
}
