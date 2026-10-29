package com.doubleclick.wadii.dispute;

import com.doubleclick.wadii.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

/** Evidence attached to a dispute: a private file, a reference to a chat message / payment proof, or a note. */
@Entity
@Immutable
@Table(name = "dispute_evidence")
@Getter
@Setter
@NoArgsConstructor
public class DisputeEvidence extends BaseEntity {

    @Column(name = "dispute_id", nullable = false)
    private Long disputeId;

    @Column(name = "submitted_by", nullable = false)
    private Long submittedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvidenceKind kind;

    @Column(name = "file_key")
    private String fileKey;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "payment_proof_id")
    private Long paymentProofId;

    private String note;
}
