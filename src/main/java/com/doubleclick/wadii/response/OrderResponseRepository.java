package com.doubleclick.wadii.response;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderResponseRepository extends JpaRepository<OrderResponse, Long> {

    List<OrderResponse> findByOrderId(Long orderId);

    Page<OrderResponse> findByOrderId(Long orderId, Pageable pageable);

    Optional<OrderResponse> findByOrderIdAndProviderId(Long orderId, Long providerId);

    boolean existsByOrderIdAndProviderId(Long orderId, Long providerId);

    long countByOrderIdAndStatus(Long orderId, ResponseStatus status);

    List<OrderResponse> findByOrderIdAndStatus(Long orderId, ResponseStatus status);

    Page<OrderResponse> findByProviderId(Long providerId, Pageable pageable);

    @Query(value = "SELECT r FROM OrderResponse r JOIN r.order o WHERE o.customer.id = :customerId",
            countQuery = "SELECT COUNT(r) FROM OrderResponse r JOIN r.order o WHERE o.customer.id = :customerId")
    Page<OrderResponse> findForCustomer(@Param("customerId") Long customerId, Pageable pageable);

    /** Pending offers whose own expiry passed; processed in batches by the expiration job. */
    @Query("SELECT r.id FROM OrderResponse r WHERE r.status = com.doubleclick.wadii.response.ResponseStatus.PENDING AND r.expiresAt <= :now")
    List<Long> findIdsOfPendingExpired(@Param("now") Instant now, Pageable pageable);
}
