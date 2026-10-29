package com.doubleclick.wadii.payment;

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

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    List<Payment> findByOrderIdOrderByCreatedAtAsc(Long orderId);

    Optional<Payment> findFirstByOrderIdAndStatusIn(Long orderId, Collection<PaymentStatus> statuses);

    Page<Payment> findByCustomerId(Long customerId, Pageable pageable);

    Page<Payment> findByProviderId(Long providerId, Pageable pageable);

    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);

    @Query("SELECT COALESCE(SUM(p.amountPaid), 0) FROM Payment p WHERE p.orderId = :orderId")
    BigDecimal sumPaid(@Param("orderId") Long orderId);

    @Query("SELECT COALESCE(SUM(p.amountRefunded), 0) FROM Payment p WHERE p.orderId = :orderId")
    BigDecimal sumRefunded(@Param("orderId") Long orderId);

    @Query("SELECT p.id FROM Payment p WHERE p.status = com.doubleclick.wadii.payment.PaymentStatus.PENDING AND p.createdAt <= :before")
    List<Long> findIdsPendingSince(@Param("before") Instant before);
}
