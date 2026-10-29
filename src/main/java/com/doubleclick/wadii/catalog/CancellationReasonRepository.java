package com.doubleclick.wadii.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CancellationReasonRepository extends JpaRepository<CancellationReason, Long> {

    List<CancellationReason> findByActiveTrue();
}
