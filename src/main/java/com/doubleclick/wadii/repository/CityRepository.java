package com.doubleclick.wadii.repository;

import com.doubleclick.wadii.entities.City;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CityRepository extends JpaRepository<City, Long> {

    Optional<City> findByName(String name);
    List<City> findByProvinceId_Id(Long provinceId);
}
