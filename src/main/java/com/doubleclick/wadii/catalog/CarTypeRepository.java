package com.doubleclick.wadii.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CarTypeRepository extends JpaRepository<CarType, Long> {

    List<CarType> findAllByOrderByNameAsc();
}
