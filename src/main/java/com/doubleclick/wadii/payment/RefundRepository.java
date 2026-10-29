package com.doubleclick.wadii.payment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    List<Refund> findByOrderIdOrderByCreatedAtAsc(Long orderId);

    List<Refund> findByPaymentId(Long paymentId);

    Page<Refund> findByStatus(RefundStatus status, Pageable pageable);

    Page<Refund> findAll(Pageable pageable);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM Refund r WHERE r.paymentId = :paymentId AND r.status IN "
            + "(com.doubleclick.wadii.payment.RefundStatus.REQUESTED, com.doubleclick.wadii.payment.RefundStatus.APPROVED, "
            + "com.doubleclick.wadii.payment.RefundStatus.PROCESSING, com.doubleclick.wadii.payment.RefundStatus.COMPLETED)")
    BigDecimal sumCommitted(@Param("paymentId") Long paymentId);

    @Query("SELECT COUNT(r) FROM Refund r WHERE r.requestedBy = :userId AND r.createdAt >= :since")
    long countRequestedSince(@Param("userId") Long userId, @Param("since") Instant since);
}
