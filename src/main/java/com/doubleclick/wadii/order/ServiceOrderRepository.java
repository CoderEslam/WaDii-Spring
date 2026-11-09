package com.doubleclick.wadii.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ServiceOrderRepository extends JpaRepository<ServiceOrder, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from ServiceOrder o where o.id = :id")
    Optional<ServiceOrder> lockById(@Param("id") Long id);

    List<ServiceOrder> findByRequestId(Long requestId);

    @Query("""
            select o from ServiceOrder o where o.customerId = :customerId and (:status is null or o.status = :status)
            order by o.id desc
            """)
    Page<ServiceOrder> forCustomer(@Param("customerId") Long customerId, @Param("status") ServiceOrder.Status status, Pageable pageable);

    @Query("""
            select o from ServiceOrder o where o.providerId = :providerId and (:status is null or o.status = :status)
            order by o.id desc
            """)
    Page<ServiceOrder> forProvider(@Param("providerId") Long providerId, @Param("status") ServiceOrder.Status status, Pageable pageable);

    @Query("""
            select o from ServiceOrder o
            where (:status is null or o.status = :status) and (:cashStatus is null or o.cashStatus = :cashStatus)
              and (:providerId is null or o.providerId = :providerId) and (:categoryId is null or o.categoryId = :categoryId)
            order by o.id desc
            """)
    Page<ServiceOrder> adminSearch(@Param("status") ServiceOrder.Status status, @Param("cashStatus") ServiceOrder.CashStatus cashStatus,
                                   @Param("providerId") Long providerId, @Param("categoryId") Long categoryId, Pageable pageable);

    /** Bookings overlapping a slot at a branch; used for basic capacity. */
    @Query("""
            select count(o) from ServiceOrder o
            where o.branchId = :branchId and o.status in :statuses and o.appointmentStart is not null
              and o.appointmentStart < :slotEnd and o.appointmentEnd > :slotStart
              and (:excludeId is null or o.id <> :excludeId)
            """)
    long countOverlapping(@Param("branchId") Long branchId, @Param("statuses") Collection<ServiceOrder.Status> statuses,
                          @Param("slotStart") Instant slotStart, @Param("slotEnd") Instant slotEnd, @Param("excludeId") Long excludeId);

    @Query("select count(o) from ServiceOrder o where o.providerId in :providerIds and o.status = com.doubleclick.wadii.order.ServiceOrder.Status.COMPLETED")
    long countCompleted(@Param("providerIds") Collection<Long> providerIds);

    @Query("""
            select o.providerId, count(o) from ServiceOrder o
            where o.providerId in :providerIds and o.status = com.doubleclick.wadii.order.ServiceOrder.Status.COMPLETED
            group by o.providerId
            """)
    List<Object[]> completedByProvider(@Param("providerIds") Collection<Long> providerIds);

    @Query("select o from ServiceOrder o where o.providerId = :providerId and o.createdAt >= :since")
    List<ServiceOrder> forProviderSince(@Param("providerId") Long providerId, @Param("since") Instant since);

    @Query("select o from ServiceOrder o where o.providerId in :providerIds and o.createdAt >= :since")
    List<ServiceOrder> forProvidersSince(@Param("providerIds") Collection<Long> providerIds, @Param("since") Instant since);

    @Query("""
            select count(o) from ServiceOrder o where o.cancellationKind in :kinds and o.canceledAt >= :since
              and ((:providerId is not null and o.providerId = :providerId) or (:customerId is not null and o.customerId = :customerId))
            """)
    long countCancellations(@Param("kinds") Collection<ServiceOrder.CancellationKind> kinds, @Param("since") Instant since,
                            @Param("providerId") Long providerId, @Param("customerId") Long customerId);

    @Query("select o.status, count(o) from ServiceOrder o where o.createdAt >= :from and o.createdAt < :to group by o.status")
    List<Object[]> countByStatus(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select o.categoryId, count(o) from ServiceOrder o where o.createdAt >= :from and o.createdAt < :to group by o.categoryId")
    List<Object[]> countByCategory(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select count(o) from ServiceOrder o where o.canceledAt >= :from and o.canceledAt < :to and o.cancellationKind in :kinds
            """)
    long countCanceledInRange(@Param("kinds") Collection<ServiceOrder.CancellationKind> kinds, @Param("from") Instant from, @Param("to") Instant to);

    @Query("select coalesce(sum(o.customerTotal), 0) from ServiceOrder o where o.providerId = :providerId and o.createdAt >= :since")
    BigDecimal grossAccepted(@Param("providerId") Long providerId, @Param("since") Instant since);
}
