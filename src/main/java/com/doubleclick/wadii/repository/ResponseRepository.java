package com.doubleclick.wadii.repository;

import com.doubleclick.wadii.entities.Responses;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ResponseRepository extends JpaRepository<Responses, Long> {

    @Query("SELECT r FROM Responses r JOIN FETCH r.provider")
    List<Responses> findAllWithProvider();

    @Query(value = "SELECT * FROM responses r WHERE r.provider_id = :providerId", nativeQuery = true)
    List<Responses> findAllByProviderId(@Param("providerId") Long providerId);

    @Query("SELECT r FROM Responses r JOIN r.order o JOIN o.user u WHERE u.id = :userId")
    List<Responses> findAllByUserId(@Param("userId") Long userId);

    List<Responses> findByOrderId(Long orderId);
}
