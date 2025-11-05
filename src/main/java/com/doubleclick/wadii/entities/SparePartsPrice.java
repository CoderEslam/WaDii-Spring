package com.doubleclick.wadii.entities;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Setter
@Getter
@Table(name = "spare_parts_price")
@AllArgsConstructor
@NoArgsConstructor
public class SparePartsPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Double price;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spare_part_id", nullable = false)
    @JsonIgnoreProperties({"sparePartsPrices", "hibernateLazyInitializer", "handler"})
    private SpareParts sparePart;

    @ManyToOne
    private Provider provider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "response_id")
    @JsonIgnoreProperties({"sparePartsPrices", "hibernateLazyInitializer", "handler"})
    private Responses response;

}
