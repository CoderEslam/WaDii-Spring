package com.doubleclick.wadii.repository;

import com.doubleclick.wadii.entities.Service;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceRepository extends JpaRepository<Service, Long> {

    Optional<Service> findByName(String name);

    List<Service> findByNameContainingIgnoreCase(String q);

}
