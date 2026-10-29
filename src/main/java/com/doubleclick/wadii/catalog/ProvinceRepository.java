package com.doubleclick.wadii.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProvinceRepository extends JpaRepository<Province, Long> {

    List<Province> findByCountryIdOrderByNameAsc(Long countryId);
}
