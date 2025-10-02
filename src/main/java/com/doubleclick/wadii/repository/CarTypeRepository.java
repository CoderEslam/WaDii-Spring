package com.doubleclick.wadii.repository;

import com.doubleclick.wadii.entities.CarType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CarTypeRepository extends JpaRepository<CarType, Long> {

    Optional<CarType> findByName(String name);

}
