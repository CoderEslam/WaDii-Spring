package com.doubleclick.wadii.repository;

import com.doubleclick.wadii.entities.Reason;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReasonRepository extends JpaRepository<Reason, Long> {

    Optional<Reason> findByReason(String reason);
}
