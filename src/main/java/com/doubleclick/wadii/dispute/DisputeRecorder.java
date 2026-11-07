package com.doubleclick.wadii.dispute;

import com.doubleclick.wadii.audit.AuditService;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.files.FileAccessRule;
import com.doubleclick.wadii.files.FilePurpose;
import com.doubleclick.wadii.files.FileService;
import com.doubleclick.wadii.fraud.FraudService;
import com.doubleclick.wadii.notification.NotificationService;
import com.doubleclick.wadii.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

/**
 * Opens disputes and records evidence. It deliberately knows nothing about orders or listings so those modules can
 * open disputes; applying an outcome lives in {@link DisputeService}.
 */
@Service
@RequiredArgsConstructor
public class DisputeRecorder implements FileAccessRule {

    public static final String FILE_REF = "DISPUTE";
    static final EnumSet<Dispute.Status> OPEN = EnumSet.of(Dispute.Status.OPEN, Dispute.Status.UNDER_REVIEW);

    private final DisputeRepository disputes;
    private final DisputeEvidenceRepository evidence;
    private final FileService files;
    private final FraudService fraud;
    private final NotificationService notifications;
    private final AuditService audit;

    public record EvidenceView(Long id, Long byUserId, String note, String fileKey, Instant createdAt) {
    }

    public record View(Long id, Dispute.Type type, Dispute.Status status, Long orderId, Long requestId, Long listingId,
                       Long warrantyClaimId, Long openedByUserId, Dispute.PartyRole openedByRole, String description,
                       List<EvidenceView> evidence, Long assignedAdminId, Dispute.Outcome outcome, String resolutionNote,
                       BigDecimal finalAmount, Instant createdAt, Instant resolvedAt) {
    }

    /** What is being disputed and between whom. */
    public record Subject(Dispute.Type type, Long orderId, Long requestId, Long listingId, Long warrantyClaimId,
                          Dispute.PartyRole openedByRole, Long respondentUserId) {
    }

    @Transactional
    public Dispute open(Long openedBy, Subject subject, String description, List<String> evidenceKeys) {
        if (subject.orderId() != null && disputes.existsByOrderIdAndTypeAndStatusIn(subject.orderId(), subject.type(), OPEN)) {
            throw BusinessException.of(ErrorCode.DISPUTE_ALREADY_OPEN);
        }
        if (subject.listingId() != null && disputes.existsByListingIdAndOpenedByAndStatusIn(subject.listingId(), openedBy, OPEN)) {
            throw BusinessException.of(ErrorCode.DISPUTE_ALREADY_OPEN);
        }
        if (description == null || description.isBlank()) {
            throw BusinessException.validation("Describe the problem.");
        }
        Dispute d = new Dispute();
        d.setType(subject.type());
        d.setOrderId(subject.orderId());
        d.setRequestId(subject.requestId());
        d.setListingId(subject.listingId());
        d.setWarrantyClaimId(subject.warrantyClaimId());
        d.setOpenedBy(openedBy);
        d.setOpenedByRole(subject.openedByRole());
        d.setRespondentUserId(subject.respondentUserId());
        d.setDescription(description.trim());
        disputes.save(d);
        files.attach(openedBy, evidenceKeys, FilePurpose.EVIDENCE, FILE_REF, d.getId())
                .forEach(f -> evidence.save(new DisputeEvidence(d.getId(), openedBy, null, f.getFileKey())));
        notifications.notify(subject.respondentUserId(), NotificationType.DISPUTE_OPENED, "Dispute opened",
                "A dispute was opened and WaDii support will review it. You can add your evidence.", "DISPUTE", d.getId());
        notifications.notify(openedBy, NotificationType.DISPUTE_OPENED, "Dispute received",
                "Support will review your dispute.", "DISPUTE", d.getId());
        fraud.afterDisputeOpened(openedBy);
        audit.record("DISPUTE_OPENED", "DISPUTE", d.getId(), subject.type().name());
        return d;
    }

    @Transactional
    public View addEvidence(AuthUser user, Long disputeId, String note, String fileKey) {
        Dispute d = disputes.findById(disputeId).orElseThrow(() -> BusinessException.of(ErrorCode.DISPUTE_NOT_FOUND));
        if (!user.admin() && !d.involves(user.id())) {
            throw BusinessException.of(ErrorCode.DISPUTE_NOT_FOUND);
        }
        if (!d.isOpen()) {
            throw BusinessException.of(ErrorCode.DISPUTE_INVALID_STATE);
        }
        if ((note == null || note.isBlank()) && fileKey == null) {
            throw BusinessException.validation("Add a note or a file.");
        }
        if (fileKey != null) {
            files.attach(user.id(), List.of(fileKey), FilePurpose.EVIDENCE, FILE_REF, disputeId);
        }
        evidence.save(new DisputeEvidence(disputeId, user.id(), note == null || note.isBlank() ? null : note.trim(), fileKey));
        Long other = user.id().equals(d.getOpenedBy()) ? d.getRespondentUserId() : d.getOpenedBy();
        notifications.notify(other, NotificationType.DISPUTE_UPDATED, "Dispute updated", "New evidence was added.", "DISPUTE", disputeId);
        return view(d);
    }

    @Transactional(readOnly = true)
    public View view(Dispute d) {
        List<EvidenceView> items = evidence.findByDisputeIdOrderByIdAsc(d.getId()).stream()
                .map(e -> new EvidenceView(e.getId(), e.getByUserId(), e.getNote(), e.getFileKey(), e.getCreatedAt())).toList();
        return new View(d.getId(), d.getType(), d.getStatus(), d.getOrderId(), d.getRequestId(), d.getListingId(),
                d.getWarrantyClaimId(), d.getOpenedBy(), d.getOpenedByRole(), d.getDescription(), items, d.getAssignedAdminId(),
                d.getOutcome(), d.getResolutionNote(), d.getFinalAmount(), d.getCreatedAt(), d.getResolvedAt());
    }

    @Override
    public boolean supports(String refType) {
        return FILE_REF.equals(refType);
    }

    @Override
    public boolean canRead(AuthUser user, String refType, Long refId) {
        return disputes.findById(refId).map(d -> d.involves(user.id())).orElse(false);
    }
}
