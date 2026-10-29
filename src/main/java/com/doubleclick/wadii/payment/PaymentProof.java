package com.doubleclick.wadii.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Private evidence of a manual transfer. Never overwritten; a proof is evidence, not a confirmation. */
@Entity
@Table(name = "payment_proofs")
@Getter
@Setter
@NoArgsConstructor
public class PaymentProof {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private Long paymentId;

    @Column(name = "file_key", nullable = false, updatable = false)
    private String fileKey;

    @Column(name = "content_type", nullable = false, updatable = false)
    private String contentType;

    @Column(name = "transaction_reference", updatable = false)
    private String transactionReference;

    @Column(name = "sender_identifier", updatable = false)
    private String senderIdentifier;

    @Column(updatable = false)
    private String note;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private Long uploadedBy;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProofStatus status = ProofStatus.PENDING_REVIEW;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    private Instant reviewedAt;

    private String rejectionReason;
}
