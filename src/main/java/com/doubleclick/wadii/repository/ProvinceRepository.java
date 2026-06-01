package com.doubleclick.wadii.repository;

import com.doubleclick.wadii.entities.Province;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProvinceRepository extends JpaRepository<Province, Long> {

    Optional<Province> findByName(String name);
    List<Province> findByCountryId_Id(Long countryId);
}
