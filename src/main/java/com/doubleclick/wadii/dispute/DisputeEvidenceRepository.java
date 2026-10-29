package com.doubleclick.wadii.dispute;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DisputeEvidenceRepository extends JpaRepository<DisputeEvidence, Long> {

    List<DisputeEvidence> findByDisputeIdOrderByCreatedAtAsc(Long disputeId);
}
