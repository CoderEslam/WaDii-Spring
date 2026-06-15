package com.doubleclick.wadii.repository;

import com.doubleclick.wadii.entities.CommonSpareParts;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommonSparePartsRepository extends JpaRepository<CommonSpareParts, Long> {

    Optional<CommonSpareParts> findByName(String name);

}
