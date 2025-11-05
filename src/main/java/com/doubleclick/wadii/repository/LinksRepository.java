package com.doubleclick.wadii.repository;

import com.doubleclick.wadii.entities.Links;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LinksRepository extends JpaRepository<Links, Long> {

    Optional<Links> findByLink(String link);

}
