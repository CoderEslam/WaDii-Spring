package com.doubleclick.wadii.fraud;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FraudFlagRepository extends JpaRepository<FraudFlag, Long> {

    boolean existsByTypeAndSubjectUserIdAndStatus(FraudFlag.Type type, Long subjectUserId, FraudFlag.Status status);

    @Query("select f from FraudFlag f where (:status is null or f.status = :status) order by f.id desc")
    Page<FraudFlag> search(@Param("status") FraudFlag.Status status, Pageable pageable);

    long countByStatus(FraudFlag.Status status);
}
