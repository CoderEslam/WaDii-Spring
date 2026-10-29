package com.doubleclick.wadii.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CountryRepository extends JpaRepository<Country, Long> {

    List<Country> findAllByOrderByNameAsc();
}
