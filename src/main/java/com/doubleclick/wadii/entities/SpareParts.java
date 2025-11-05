package com.doubleclick.wadii.entities;


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
@Table(name = "spare_parts")
@AllArgsConstructor
@NoArgsConstructor
public class SpareParts {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String sparePartName;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnoreProperties({"spareParts", "hibernateLazyInitializer", "handler"})
    private Order order;

    @OneToMany(mappedBy = "sparePart",fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties({"sparePart", "response", "hibernateLazyInitializer", "handler"})
    private List<SparePartsPrice> sparePartsPrices;


    public SpareParts(Long id) {
        this.id = id;
    }
}
