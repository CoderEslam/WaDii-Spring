package com.doubleclick.wadii.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class SparePartsPriceDto {

    private Long id;
    private BigDecimal price;
}
