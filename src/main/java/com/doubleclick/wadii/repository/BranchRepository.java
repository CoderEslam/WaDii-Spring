package com.doubleclick.wadii.repository;

import com.doubleclick.wadii.entities.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    List<Branch> findAllByProviderId(Long providerId);

}
