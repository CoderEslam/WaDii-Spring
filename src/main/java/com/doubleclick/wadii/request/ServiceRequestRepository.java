package com.doubleclick.wadii.request;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ServiceRequest r where r.id = :id")
    Optional<ServiceRequest> lockById(@Param("id") Long id);

    @Query("""
            select r from ServiceRequest r where r.customerId = :customerId and (:status is null or r.status = :status)
            order by r.id desc
            """)
    Page<ServiceRequest> mine(@Param("customerId") Long customerId, @Param("status") ServiceRequest.Status status, Pageable pageable);

    @Query("select r from ServiceRequest r where (:status is null or r.status = :status) order by r.id desc")
    Page<ServiceRequest> adminSearch(@Param("status") ServiceRequest.Status status, Pageable pageable);

    @Query("select r.id from ServiceRequest r where r.status in :statuses and r.responseDeadline <= :now")
    List<Long> deadlinePassed(@Param("statuses") Collection<ServiceRequest.Status> statuses, @Param("now") Instant now);

    long countByCreatedAtBetween(Instant from, Instant to);

    @Query("select r.status, count(r) from ServiceRequest r where r.createdAt >= :from and r.createdAt < :to group by r.status")
    List<Object[]> countByStatus(@Param("from") Instant from, @Param("to") Instant to);
}
