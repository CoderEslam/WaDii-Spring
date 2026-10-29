package com.doubleclick.wadii.order;

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

public interface OrderRepository extends JpaRepository<Order, Long> {

    /** Row lock taken by every multi-row order transition (accept, cancel, expire, payment confirmation). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    Page<Order> findByCustomerId(Long customerId, Pageable pageable);

    Page<Order> findByCustomerIdAndStatusIn(Long customerId, Collection<OrderStatus> statuses, Pageable pageable);

    Page<Order> findByStatusIn(Collection<OrderStatus> statuses, Pageable pageable);

    @Query("SELECT o.id FROM Order o WHERE o.status IN :statuses AND o.responseDeadline <= :now")
    List<Long> findIdsPastDeadline(@Param("statuses") Collection<OrderStatus> statuses, @Param("now") Instant now);

    @Query("SELECT o.id FROM Order o WHERE o.status = com.doubleclick.wadii.order.OrderStatus.MATCHING AND o.responseDeadline > :now")
    List<Long> findIdsAwaitingMatch(@Param("now") Instant now);

    @Query("SELECT o.id FROM Order o WHERE o.status = com.doubleclick.wadii.order.OrderStatus.IN_PROGRESS "
            + "AND o.providerCompletedAt IS NOT NULL AND o.providerCompletedAt <= :before")
    List<Long> findIdsAwaitingAutoComplete(@Param("before") Instant before);

    long countByCustomerId(Long customerId);

    long countByAcceptedProviderIdAndStatusIn(Long providerId, Collection<OrderStatus> statuses);

    long countByCustomerIdAndStatus(Long customerId, OrderStatus status);
}
