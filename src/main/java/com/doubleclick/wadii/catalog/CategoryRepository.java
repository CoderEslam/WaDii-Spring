package com.doubleclick.wadii.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<ServiceCategory, Long> {
    List<ServiceCategory> findAllByOrderBySortOrderAscIdAsc();

    List<ServiceCategory> findByActiveTrueOrderBySortOrderAscIdAsc();
}
