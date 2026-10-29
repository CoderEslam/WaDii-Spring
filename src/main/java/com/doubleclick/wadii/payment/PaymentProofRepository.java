package com.doubleclick.wadii.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PaymentProofRepository extends JpaRepository<PaymentProof, Long> {

    List<PaymentProof> findByPaymentIdOrderByUploadedAtAsc(Long paymentId);

    long countByPaymentId(Long paymentId);

    /** Same transaction reference reused on another payment: a fraud signal. */
    @Query("SELECT COUNT(p) FROM PaymentProof p WHERE LOWER(p.transactionReference) = LOWER(:ref) AND p.paymentId <> :paymentId")
    long countReferenceElsewhere(@Param("ref") String reference, @Param("paymentId") Long paymentId);
}
