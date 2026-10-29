package com.doubleclick.wadii.dispute;

import com.doubleclick.wadii.audit.AuditAction;
import com.doubleclick.wadii.audit.AuditService;
import com.doubleclick.wadii.audit.FraudFlagType;
import com.doubleclick.wadii.audit.FraudService;
import com.doubleclick.wadii.chat.ConversationRepository;
import com.doubleclick.wadii.chat.Message;
import com.doubleclick.wadii.chat.MessageRepository;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.config.WadiiProperties;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.identity.AdminRole;
import com.doubleclick.wadii.identity.Role;
import com.doubleclick.wadii.identity.UserRepository;
import com.doubleclick.wadii.notification.NotificationService;
import com.doubleclick.wadii.notification.NotificationType;
import com.doubleclick.wadii.order.ActorRole;
import com.doubleclick.wadii.order.Order;
import com.doubleclick.wadii.order.OrderAccess;
import com.doubleclick.wadii.order.OrderTimelineService;
import com.doubleclick.wadii.order.TimelineEvent;
import com.doubleclick.wadii.payment.Payment;
import com.doubleclick.wadii.payment.PaymentProof;
import com.doubleclick.wadii.payment.PaymentProofRepository;
import com.doubleclick.wadii.payment.PaymentRepository;
import com.doubleclick.wadii.payment.PaymentService;
import com.doubleclick.wadii.payment.PaymentStatus;
import com.doubleclick.wadii.payment.RefundService;
import com.doubleclick.wadii.payment.RefundSource;
import com.doubleclick.wadii.provider.ProviderAccess;
import com.doubleclick.wadii.storage.FileCategory;
import com.doubleclick.wadii.storage.FileStorageService;
import com.doubleclick.wadii.storage.StoredFile;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Disputes between the customer and the accepted provider of an order, investigated by support / finance admins.
 * Evidence is private: parties and support/finance admins only.
 */
@Service
@RequiredArgsConstructor
public class DisputeService {

    private static final Set<DisputeStatus> OPEN = EnumSet.of(DisputeStatus.OPEN, DisputeStatus.UNDER_REVIEW,
            DisputeStatus.WAITING_CUSTOMER, DisputeStatus.WAITING_PROVIDER);

    private final DisputeRepository disputeRepository;
    private final DisputeEvidenceRepository evidenceRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentProofRepository proofRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final OrderAccess orderAccess;
    private final ProviderAccess providerAccess;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final FileStorageService storage;
    private final NotificationService notifications;
    private final AuditService audit;
    private final FraudService fraud;
    private final OrderTimelineService timeline;
    private final WadiiProperties properties;
    private final Clock clock;

    public record OpenRequest(@NotNull DisputeType type, @NotBlank @Size(max = 255) String reason,
                              @Size(max = 4000) String description, Long paymentId) {
    }

    public record StatusRequest(@NotNull DisputeStatus status, @Size(max = 2000) String note) {
    }

    public record ResolveRequest(@NotNull DisputeOutcome outcome, @NotBlank @Size(max = 4000) String resolution,
                                 @DecimalMin("0.01") BigDecimal refundAmount) {
    }

    public record EvidenceView(Long id, Long submittedBy, EvidenceKind kind, boolean hasFile, String contentType, Long messageId,
                               Long paymentProofId, String note, Instant createdAt) {
        static EvidenceView of(DisputeEvidence e) {
            return new EvidenceView(e.getId(), e.getSubmittedBy(), e.getKind(), e.getFileKey() != null, e.getContentType(),
                    e.getMessageId(), e.getPaymentProofId(), e.getNote(), e.getCreatedAt());
        }
    }

    public record DisputeView(Long id, Long orderId, Long paymentId, Long customerId, Long providerId, Long openedBy,
                              DisputeType type, DisputeStatus status, String reason, String description, String resolution,
                              DisputeOutcome resolutionOutcome, Long resolvedBy, Instant createdAt, Instant updatedAt,
                              Instant resolvedAt, List<EvidenceView> evidence) {
    }

    @Transactional
    public DisputeView open(AuthUser me, Long orderId, OpenRequest r) {
        Order order = orderAccess.loadForUpdate(orderId);
        OrderAccess.Relation rel = orderAccess.requireParty(me, order);
        if (rel == OrderAccess.Relation.ADMIN) throw BusinessException.forbidden("Disputes are opened by the customer or the provider");
        if (order.getAcceptedProvider() == null) throw BusinessException.of(ErrorCode.ORDER_INVALID_STATE, "No provider was assigned to this order");
        return create(me, order, r, rel == OrderAccess.Relation.CUSTOMER ? ActorRole.CUSTOMER : ActorRole.PROVIDER);
    }

    /** Also used by warranty escalation (WARRANTY_DISPUTE). */
    @Transactional(propagation = Propagation.MANDATORY)
    public DisputeView create(AuthUser me, Order order, OpenRequest r, ActorRole role) {
        if (disputeRepository.existsByOrderIdAndTypeAndStatusIn(order.getId(), r.type(), OPEN)) {
            throw BusinessException.of(ErrorCode.DISPUTE_ALREADY_OPEN);
        }
        Payment payment = null;
        if (r.type() == DisputeType.PAYMENT_DISPUTE) {
            if (r.paymentId() == null) throw BusinessException.validation("paymentId is required for a payment dispute");
            payment = paymentRepository.findByIdForUpdate(r.paymentId())
                    .filter(p -> p.getOrderId().equals(order.getId()))
                    .orElseThrow(() -> BusinessException.notFound(ErrorCode.PAYMENT_NOT_FOUND));
            paymentService.markDisputed(payment);
        }
        Dispute d = new Dispute();
        d.setOrderId(order.getId());
        d.setPaymentId(payment == null ? null : payment.getId());
        d.setCustomerId(order.getCustomer().getId());
        d.setProviderId(order.getAcceptedProvider().getId());
        d.setOpenedBy(me.id());
        d.setType(r.type());
        d.setReason(r.reason().trim());
        d.setDescription(r.description());
        d = disputeRepository.save(d);

        Instant since = clock.instant().minus(Duration.ofDays(30));
        if (disputeRepository.countOpenedSince(me.id(), since) > properties.getFraud().getMaxDisputesPer30Days()) {
            fraud.flag(me.id(), FraudFlagType.EXCESSIVE_DISPUTES, "DISPUTE", d.getId(), "more than "
                    + properties.getFraud().getMaxDisputesPer30Days() + " disputes in 30 days");
        }
        audit.record(me, role, payment != null ? AuditAction.PAYMENT_DISPUTED : AuditAction.DISPUTE_OPENED, "DISPUTE", d.getId(), r.type().name());
        timeline.add(order.getId(), payment != null ? TimelineEvent.PAYMENT_DISPUTED : TimelineEvent.DISPUTE_OPENED, me.id(), role, r.type() + ": " + r.reason());
        Long other = role == ActorRole.CUSTOMER ? order.getAcceptedProvider().getUser().getId() : order.getCustomer().getId();
        NotificationType type = payment != null ? NotificationType.PAYMENT_DISPUTED : NotificationType.DISPUTE_OPENED;
        String text = r.type() + " opened on order #" + order.getId() + ": " + r.reason();
        notifications.notify(other, type, "Dispute opened", text, order.getId());
        notifications.notify(me.id(), type, "Dispute opened", "Your dispute on order #" + order.getId() + " was sent to WaDii support.", order.getId());
        AdminRole team = payment != null || r.type() == DisputeType.REFUND_DISPUTE ? AdminRole.FINANCE_ADMIN : AdminRole.SUPPORT_ADMIN;
        for (Long adminId : adminsWith(team)) {
            notifications.notify(adminId, type, "New dispute", text, order.getId());
        }
        return view(d);
    }

    @Transactional
    public EvidenceView addEvidence(AuthUser me, Long disputeId, EvidenceKind kind, MultipartFile file, Long messageId,
                                    Long paymentProofId, String note) {
        Dispute d = load(disputeId);
        requireAccess(me, d);
        if (!d.getStatus().isOpen()) throw BusinessException.of(ErrorCode.DISPUTE_INVALID_STATE);
        DisputeEvidence e = new DisputeEvidence();
        e.setDisputeId(d.getId());
        e.setSubmittedBy(me.id());
        e.setKind(kind);
        e.setNote(note == null || note.isBlank() ? null : note.trim());
        if (file != null && !file.isEmpty()) {
            StoredFile stored = storage.store(file, FileCategory.DISPUTE_EVIDENCE);
            e.setFileKey(stored.key());
            e.setContentType(stored.contentType());
        }
        if (messageId != null) {
            Message m = messageRepository.findById(messageId).orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND));
            boolean sameOrder = conversationRepository.findById(m.getConversationId()).map(c -> c.getOrderId().equals(d.getOrderId())).orElse(false);
            if (!sameOrder) throw BusinessException.validation("The message does not belong to this order's conversation");
            e.setMessageId(messageId);
        }
        if (paymentProofId != null) {
            PaymentProof proof = proofRepository.findById(paymentProofId).orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND));
            boolean sameOrder = paymentRepository.findById(proof.getPaymentId()).map(p -> p.getOrderId().equals(d.getOrderId())).orElse(false);
            if (!sameOrder) throw BusinessException.validation("The payment proof does not belong to this order");
            e.setPaymentProofId(paymentProofId);
        }
        if (e.getFileKey() == null && e.getMessageId() == null && e.getPaymentProofId() == null && e.getNote() == null) {
            throw BusinessException.validation("Evidence needs a file, a message, a payment proof or a note");
        }
        return EvidenceView.of(evidenceRepository.save(e));
    }

    @Transactional
    public DisputeView changeStatus(AuthUser admin, Long disputeId, StatusRequest r) {
        Dispute d = load(disputeId);
        requireTeam(admin, d);
        if (!d.getStatus().isOpen()) throw BusinessException.of(ErrorCode.DISPUTE_INVALID_STATE);
        if (!EnumSet.of(DisputeStatus.UNDER_REVIEW, DisputeStatus.WAITING_CUSTOMER, DisputeStatus.WAITING_PROVIDER).contains(r.status())) {
            throw BusinessException.validation("Use resolve/reject to close a dispute");
        }
        d.setStatus(r.status());
        audit.record(admin, ActorRole.ADMIN, AuditAction.DISPUTE_UPDATED, "DISPUTE", d.getId(), r.status() + (r.note() == null ? "" : ": " + r.note()));
        String msg = "Dispute #" + d.getId() + " on order #" + d.getOrderId() + " is now " + r.status() + (r.note() == null ? "" : ": " + r.note());
        notifyParties(d, msg);
        return view(d);
    }

    /**
     * Closes a dispute with a decision. Payment outcomes confirm or reject the disputed payment; REFUND_CUSTOMER
     * creates an approved refund. Financial outcomes need a FINANCE admin.
     */
    @Transactional
    public DisputeView resolve(AuthUser admin, Long disputeId, ResolveRequest r) {
        Dispute d = load(disputeId);
        requireTeam(admin, d);
        if (!d.getStatus().isOpen()) throw BusinessException.of(ErrorCode.DISPUTE_INVALID_STATE);
        Order order = orderAccess.loadForUpdate(d.getOrderId());
        boolean financial = r.outcome() == DisputeOutcome.PAYMENT_CONFIRMED || r.outcome() == DisputeOutcome.PAYMENT_REJECTED
                || r.outcome() == DisputeOutcome.REFUND_CUSTOMER;
        if (financial && !admin.isFinance()) throw BusinessException.forbidden("Financial outcomes need a FINANCE admin");
        switch (r.outcome()) {
            case PAYMENT_CONFIRMED, PAYMENT_REJECTED -> {
                if (d.getPaymentId() == null) throw BusinessException.validation("This dispute has no payment");
                Payment p = paymentRepository.findByIdForUpdate(d.getPaymentId()).orElseThrow();
                paymentService.resolveDisputed(p, r.outcome() == DisputeOutcome.PAYMENT_CONFIRMED, admin.id(), r.resolution());
                audit.record(admin, ActorRole.ADMIN, r.outcome() == DisputeOutcome.PAYMENT_CONFIRMED ? AuditAction.PAYMENT_CONFIRMED : AuditAction.PAYMENT_REJECTED,
                        "PAYMENT", p.getId(), "dispute " + d.getId());
            }
            case REFUND_CUSTOMER -> {
                if (r.refundAmount() == null) throw BusinessException.validation("refundAmount is required");
                Payment p = paymentService.confirmedPayments(order.getId()).stream()
                        .filter(x -> x.getAmountPaid().compareTo(x.getAmountRefunded()) > 0).findFirst()
                        .orElseThrow(() -> BusinessException.of(ErrorCode.REFUND_INVALID_STATE, "There is no confirmed payment to refund"));
                refundService.create(order, p, r.refundAmount(), RefundSource.DISPUTE, "Dispute #" + d.getId() + ": " + r.resolution(),
                        admin.id(), d.getId(), true);
            }
            default -> {
            }
        }
        if (d.getPaymentId() != null && r.outcome() != DisputeOutcome.PAYMENT_CONFIRMED && r.outcome() != DisputeOutcome.PAYMENT_REJECTED) {
            Payment p = paymentRepository.findByIdForUpdate(d.getPaymentId()).orElseThrow();
            if (p.getStatus() == PaymentStatus.DISPUTED) paymentService.resolveDisputed(p, false, admin.id(), r.resolution());
        }
        close(d, DisputeStatus.RESOLVED, r.outcome(), r.resolution(), admin);
        return view(d);
    }

    /** Dismisses the claim. A disputed payment goes back to REJECTED (the transfer was not proven). */
    @Transactional
    public DisputeView reject(AuthUser admin, Long disputeId, String resolution) {
        Dispute d = load(disputeId);
        requireTeam(admin, d);
        if (!d.getStatus().isOpen()) throw BusinessException.of(ErrorCode.DISPUTE_INVALID_STATE);
        if (d.getPaymentId() != null) {
            Payment p = paymentRepository.findByIdForUpdate(d.getPaymentId()).orElseThrow();
            if (p.getStatus() == PaymentStatus.DISPUTED) paymentService.resolveDisputed(p, false, admin.id(), resolution);
        }
        close(d, DisputeStatus.REJECTED, DisputeOutcome.NO_ACTION, resolution, admin);
        return view(d);
    }

    /** The opener withdraws an open dispute. */
    @Transactional
    public DisputeView cancel(AuthUser me, Long disputeId) {
        Dispute d = load(disputeId);
        if (!d.getOpenedBy().equals(me.id())) throw BusinessException.forbidden("Only the person who opened the dispute can cancel it");
        if (d.getStatus() != DisputeStatus.OPEN) throw BusinessException.of(ErrorCode.DISPUTE_INVALID_STATE);
        if (d.getPaymentId() != null) {
            Payment p = paymentRepository.findByIdForUpdate(d.getPaymentId()).orElseThrow();
            if (p.getStatus() == PaymentStatus.DISPUTED) {
                p.setStatus(p.getRejectedAt() != null ? PaymentStatus.REJECTED : PaymentStatus.PROOF_SUBMITTED);
                p.setDisputedAt(null);
            }
        }
        d.setStatus(DisputeStatus.CANCELED);
        d.setResolvedAt(clock.instant());
        audit.record(me, AuditService.roleOf(me), AuditAction.DISPUTE_UPDATED, "DISPUTE", d.getId(), "canceled by opener");
        return view(d);
    }

    @Transactional(readOnly = true)
    public DisputeView get(AuthUser me, Long disputeId) {
        Dispute d = load(disputeId);
        requireAccess(me, d);
        return view(d);
    }

    @Transactional(readOnly = true)
    public PageResponse<DisputeView> mine(AuthUser me, Integer page, Integer size) {
        var pageable = PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = providerAccess.findOwn(me).map(p -> disputeRepository.findByProviderId(p.getId(), pageable))
                .orElseGet(() -> disputeRepository.findByCustomerId(me.id(), pageable));
        return PageResponse.of(result, this::view);
    }

    @Transactional(readOnly = true)
    public PageResponse<DisputeView> adminList(Set<DisputeStatus> statuses, Integer page, Integer size) {
        var pageable = PageResponse.pageable(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
        return PageResponse.of(disputeRepository.findByStatusIn(statuses == null || statuses.isEmpty() ? OPEN : statuses, pageable), this::view);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> evidenceFile(AuthUser me, Long evidenceId) {
        DisputeEvidence e = evidenceRepository.findById(evidenceId).orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND));
        requireAccess(me, load(e.getDisputeId()));
        if (e.getFileKey() == null) throw BusinessException.notFound(ErrorCode.NOT_FOUND);
        return storage.servePrivate(e.getFileKey(), e.getContentType());
    }

    private void close(Dispute d, DisputeStatus status, DisputeOutcome outcome, String resolution, AuthUser admin) {
        d.setStatus(status);
        d.setResolutionOutcome(outcome);
        d.setResolution(resolution);
        d.setResolvedBy(admin.id());
        d.setResolvedAt(clock.instant());
        audit.record(admin, ActorRole.ADMIN, AuditAction.DISPUTE_RESOLVED, "DISPUTE", d.getId(), status + " " + outcome + ": " + resolution);
        timeline.add(d.getOrderId(), TimelineEvent.DISPUTE_RESOLVED, admin.id(), ActorRole.ADMIN, status + " " + outcome);
        notifyParties(d, "Dispute #" + d.getId() + " on order #" + d.getOrderId() + " was " + status.name().toLowerCase() + ": " + resolution);
    }

    private void notifyParties(Dispute d, String message) {
        notifications.notify(d.getCustomerId(), NotificationType.DISPUTE_UPDATED, "Dispute update", message, d.getOrderId());
        Long providerUserId = providerAccess.require(d.getProviderId()).getUser().getId();
        notifications.notify(providerUserId, NotificationType.DISPUTE_UPDATED, "Dispute update", message, d.getOrderId());
    }

    private void requireAccess(AuthUser me, Dispute d) {
        boolean customer = d.getCustomerId().equals(me.id());
        boolean provider = providerAccess.findOwn(me).map(p -> p.getId().equals(d.getProviderId())).orElse(false);
        if (!customer && !provider && !me.isSupport() && !me.isFinance()) throw BusinessException.forbidden("You are not allowed to access this dispute");
    }

    /** Payment and refund disputes belong to FINANCE; the rest to SUPPORT. */
    private void requireTeam(AuthUser admin, Dispute d) {
        boolean finance = d.getType() == DisputeType.PAYMENT_DISPUTE || d.getType() == DisputeType.REFUND_DISPUTE;
        if (finance ? !admin.isFinance() : !admin.isSupport()) throw BusinessException.forbidden();
    }

    private List<Long> adminsWith(AdminRole role) {
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN && u.isActive() && u.effectiveAdminRoles().contains(role))
                .map(u -> u.getId()).limit(50).toList();
    }

    private Dispute load(Long id) {
        return disputeRepository.findById(id).orElseThrow(() -> BusinessException.notFound(ErrorCode.DISPUTE_NOT_FOUND));
    }

    private DisputeView view(Dispute d) {
        return new DisputeView(d.getId(), d.getOrderId(), d.getPaymentId(), d.getCustomerId(), d.getProviderId(), d.getOpenedBy(),
                d.getType(), d.getStatus(), d.getReason(), d.getDescription(), d.getResolution(), d.getResolutionOutcome(),
                d.getResolvedBy(), d.getCreatedAt(), d.getUpdatedAt(), d.getResolvedAt(),
                evidenceRepository.findByDisputeIdOrderByCreatedAtAsc(d.getId()).stream().map(EvidenceView::of).toList());
    }
}
