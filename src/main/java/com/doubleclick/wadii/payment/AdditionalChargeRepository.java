package com.doubleclick.wadii.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface AdditionalChargeRepository extends JpaRepository<AdditionalCharge, Long> {

    List<AdditionalCharge> findByOrderIdOrderByCreatedAtAsc(Long orderId);

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM AdditionalCharge c WHERE c.orderId = :orderId "
            + "AND c.status = com.doubleclick.wadii.payment.ChargeStatus.APPROVED")
    BigDecimal sumApproved(@Param("orderId") Long orderId);
}
