package com.doubleclick.wadii.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommonSparePartRepository extends JpaRepository<CommonSparePart, Long> {

    List<CommonSparePart> findAllByOrderByNameAsc();
}
