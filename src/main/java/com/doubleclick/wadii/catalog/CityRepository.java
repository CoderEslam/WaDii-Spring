package com.doubleclick.wadii.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CityRepository extends JpaRepository<City, Long> {

    List<City> findByProvinceIdOrderByNameAsc(Long provinceId);
}
