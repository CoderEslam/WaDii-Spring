package com.doubleclick.wadii.entities;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

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
    @Column(precision = 19, scale = 2)
    private BigDecimal price;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spare_part_id", nullable = false)
    @JsonIgnoreProperties({"sparePartsPrices","order", "hibernateLazyInitializer", "handler"})
    private SpareParts sparePart;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "response_id")
    @JsonIgnoreProperties({"sparePartsPrices", "provider", "order", "hibernateLazyInitializer", "handler"})
    private Responses response;

}
