package com.doubleclick.wadii.dispute;

import com.doubleclick.wadii.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A note or file added to a dispute by a party or staff. Append-only. */
@Getter
@Entity
@Table(name = "dispute_evidence")
@NoArgsConstructor
public class DisputeEvidence extends BaseEntity {

    @Column(name = "dispute_id", nullable = false)
    private Long disputeId;

    @Column(name = "by_user_id", nullable = false)
    private Long byUserId;

    private String note;

    @Column(name = "file_key")
    private String fileKey;

    public DisputeEvidence(Long disputeId, Long byUserId, String note, String fileKey) {
        this.disputeId = disputeId;
        this.byUserId = byUserId;
        this.note = note;
        this.fileKey = fileKey;
    }
}
