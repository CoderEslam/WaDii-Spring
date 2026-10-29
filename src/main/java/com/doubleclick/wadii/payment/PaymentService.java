package com.doubleclick.wadii.payment;

import com.doubleclick.wadii.audit.AuditAction;
import com.doubleclick.wadii.audit.AuditService;
import com.doubleclick.wadii.audit.FraudFlagType;
import com.doubleclick.wadii.audit.FraudService;
import com.doubleclick.wadii.common.config.WadiiProperties;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.notification.NotificationService;
import com.doubleclick.wadii.notification.NotificationType;
import com.doubleclick.wadii.order.ActorRole;
import com.doubleclick.wadii.order.Order;
import com.doubleclick.wadii.order.OrderAccess;
import com.doubleclick.wadii.order.OrderTimelineService;
import com.doubleclick.wadii.order.TimelineEvent;
import com.doubleclick.wadii.payment.dto.PaymentDtos.OrderFinancials;
import com.doubleclick.wadii.payment.dto.PaymentDtos.PaymentView;
import com.doubleclick.wadii.payment.dto.PaymentDtos.ChargeView;
import com.doubleclick.wadii.payment.dto.PaymentDtos.RefundView;
import com.doubleclick.wadii.provider.Provider;
import com.doubleclick.wadii.provider.ProviderAccess;
import com.doubleclick.wadii.provider.ProviderPaymentAccount;
import com.doubleclick.wadii.provider.ProviderPaymentAccountRepository;
import com.doubleclick.wadii.storage.FileCategory;
import com.doubleclick.wadii.storage.FileStorageService;
import com.doubleclick.wadii.storage.StoredFile;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Payment lifecycle for manual methods. Key rules:
 * <ul>
 *   <li>Amounts come from {@link PaymentLedger}, never from the client.</li>
 *   <li>A proof is evidence, never a confirmation: only the accepted provider (from the JWT) confirms.</li>
 *   <li>History is preserved: proofs are append-only; changing the method after a proof cancels the payment and
 *       opens a new one.</li>
 *   <li>Payment status is independent from order status.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentProofRepository proofRepository;
    private final RefundRepository refundRepository;
    private final AdditionalChargeRepository chargeRepository;
    private final ProviderPaymentAccountRepository accountRepository;
    private final PaymentLedger ledger;
    private final OrderAccess orderAccess;
    private final ProviderAccess providerAccess;
    private final FileStorageService storage;
    private final NotificationService notifications;
    private final AuditService audit;
    private final FraudService fraud;
    private final OrderTimelineService timeline;
    private final WadiiProperties properties;
    private final Clock clock;

    // ------------------------------------------------------------------ creation

    /** Opens a payment for whatever is outstanding, unless one is already active. Called inside order transactions. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Payment> openForOutstanding(Order order) {
        if (order.getAcceptedProvider() == null || !order.getStatus().isAssigned()) return Optional.empty();
        if (paymentRepository.findFirstByOrderIdAndStatusIn(order.getId(), PaymentStatus.ACTIVE).isPresent()) return Optional.empty();
        BigDecimal outstanding = ledger.outstanding(order);
        if (outstanding.signum() <= 0) return Optional.empty();
        Payment p = newPayment(order, outstanding);
        notifications.notify(order.getCustomer().getId(), NotificationType.PAYMENT_CREATED, "Payment due",
                "Pay " + outstanding + " " + order.getCurrency() + " for order #" + order.getId() + ".", order.getId());
        return Optional.of(p);
    }

    private Payment newPayment(Order order, BigDecimal amount) {
        Payment p = new Payment();
        p.setOrderId(order.getId());
        p.setCustomerId(order.getCustomer().getId());
        p.setProviderId(order.getAcceptedProvider().getId());
        p.setAmount(amount);
        p.setCurrency(order.getCurrency());
        p = paymentRepository.save(p);
        audit.system(AuditAction.PAYMENT_CREATED, "PAYMENT", p.getId(), "order=" + order.getId() + " amount=" + amount);
        timeline.system(order.getId(), TimelineEvent.PAYMENT_CREATED, amount + " " + order.getCurrency());
        return p;
    }

    // ------------------------------------------------------------------ customer actions

    /**
     * Selects / changes the method. PENDING: changed in place (no evidence exists yet).
     * PROOF_SUBMITTED: the payment is CANCELED and a new one opened, preserving the old proofs.
     * REJECTED / EXPIRED: a new payment is opened for the outstanding amount.
     */
    @Transactional
    public PaymentView selectMethod(AuthUser me, Long paymentId, PaymentMethod method) {
        Payment current = lockPayment(paymentId);
        Order order = orderAccess.loadForUpdate(current.getOrderId());
        orderAccess.requireCustomer(me, order);
        if (!method.isSupported()) {
            throw BusinessException.of(ErrorCode.PAYMENT_METHOD_NOT_SUPPORTED);
        }
        Payment target = switch (current.getStatus()) {
            case PENDING -> current;
            case PROOF_SUBMITTED -> {
                close(current, PaymentStatus.CANCELED);
                audit.record(me, ActorRole.CUSTOMER, AuditAction.PAYMENT_CANCELED, "PAYMENT", current.getId(), "method changed after proof");
                paymentRepository.flush();
                yield newPayment(order, current.getAmount());
            }
            case REJECTED, EXPIRED, CANCELED -> {
                if (!order.getStatus().isAssigned()) throw BusinessException.of(ErrorCode.ORDER_INVALID_STATE);
                if (paymentRepository.findFirstByOrderIdAndStatusIn(order.getId(), PaymentStatus.ACTIVE).isPresent()) {
                    throw BusinessException.of(ErrorCode.PAYMENT_INVALID_STATE, "Another payment is already open for this order");
                }
                BigDecimal outstanding = ledger.outstanding(order);
                if (outstanding.signum() <= 0) throw BusinessException.of(ErrorCode.PAYMENT_NOTHING_DUE);
                yield newPayment(order, outstanding);
            }
            default -> throw BusinessException.of(ErrorCode.PAYMENT_INVALID_STATE);
        };
        if (method.usesProof()) {
            ProviderPaymentAccount account = accountRepository.findByProviderIdAndMethodAndActiveTrue(target.getProviderId(), method)
                    .orElseThrow(() -> BusinessException.of(ErrorCode.PAYMENT_ACCOUNT_MISSING));
            target.setPayeeAccountName(account.getAccountName());
            target.setPayeeAccountIdentifier(account.getAccountIdentifier());
        } else {
            target.setPayeeAccountName(null);
            target.setPayeeAccountIdentifier(null);
        }
        target.setMethod(method);
        target.setMethodSelectedAt(clock.instant());
        audit.record(me, ActorRole.CUSTOMER, AuditAction.PAYMENT_METHOD_SELECTED, "PAYMENT", target.getId(), method.name());
        timeline.add(order.getId(), TimelineEvent.PAYMENT_METHOD_SELECTED, me.id(), ActorRole.CUSTOMER, method.name());
        return view(target);
    }

    /** Uploads a transfer proof. Moves the payment to PROOF_SUBMITTED; it does NOT confirm it. */
    @Transactional
    public PaymentView uploadProof(AuthUser me, Long paymentId, MultipartFile file, String transactionReference,
                                   String senderIdentifier, String note) {
        Payment p = lockPayment(paymentId);
        Order order = orderAccess.load(p.getOrderId());
        orderAccess.requireCustomer(me, order);
        if (p.getMethod() == null) throw BusinessException.of(ErrorCode.PAYMENT_METHOD_REQUIRED);
        if (!p.getMethod().usesProof()) throw BusinessException.of(ErrorCode.PAYMENT_PROOF_NOT_ALLOWED);
        if (p.getStatus() != PaymentStatus.PENDING && p.getStatus() != PaymentStatus.PROOF_SUBMITTED) {
            throw BusinessException.of(ErrorCode.PAYMENT_INVALID_STATE);
        }
        long existing = proofRepository.countByPaymentId(p.getId());
        if (existing >= properties.getPayment().getMaxProofsPerPayment()) {
            throw BusinessException.of(ErrorCode.PAYMENT_PROOF_LIMIT);
        }
        StoredFile stored = storage.store(file, FileCategory.PAYMENT_PROOF);
        PaymentProof proof = new PaymentProof();
        proof.setPaymentId(p.getId());
        proof.setFileKey(stored.key());
        proof.setContentType(stored.contentType());
        proof.setTransactionReference(trim(transactionReference, 100));
        proof.setSenderIdentifier(trim(senderIdentifier, 150));
        proof.setNote(trim(note, 1000));
        proof.setUploadedBy(me.id());
        proof.setUploadedAt(clock.instant());
        proofRepository.save(proof);
        p.setStatus(PaymentStatus.PROOF_SUBMITTED);
        p.setSubmittedAt(clock.instant());

        if (existing + 1 >= 3) {
            fraud.flag(me.id(), FraudFlagType.REPEATED_PAYMENT_PROOFS, "PAYMENT", p.getId(), (existing + 1) + " proofs on one payment");
        }
        if (proof.getTransactionReference() != null && proofRepository.countReferenceElsewhere(proof.getTransactionReference(), p.getId()) > 0) {
            fraud.flag(me.id(), FraudFlagType.DUPLICATE_TRANSACTION_REFERENCE, "PAYMENT", p.getId(),
                    "transaction reference already used on another payment");
        }
        audit.record(me, ActorRole.CUSTOMER, AuditAction.PAYMENT_PROOF_UPLOADED, "PAYMENT", p.getId(), "proof " + proof.getId());
        timeline.add(order.getId(), TimelineEvent.PAYMENT_PROOF_UPLOADED, me.id(), ActorRole.CUSTOMER, p.getMethod().name());
        Long providerUserId = order.getAcceptedProvider().getUser().getId();
        notifications.notify(providerUserId, NotificationType.PAYMENT_PROOF_SUBMITTED, "Payment proof received",
                "The customer uploaded a " + p.getMethod() + " proof for order #" + order.getId()
                        + ". Check your account and confirm only if the money arrived.", order.getId());
        notifications.notify(me.id(), NotificationType.PAYMENT_PROOF_SUBMITTED, "Proof submitted",
                "Your proof for order #" + order.getId() + " was sent to the provider for verification.", order.getId());
        return view(p);
    }

    // ------------------------------------------------------------------ provider actions

    /**
     * The accepted provider confirms the money actually arrived. Vodafone Cash / InstaPay need a submitted proof;
     * CASH is confirmed directly once received. Never trusts a provider id from the request.
     */
    @Transactional
    public PaymentView confirm(AuthUser me, Long paymentId) {
        Payment p = lockPayment(paymentId);
        Order order = orderAccess.loadForUpdate(p.getOrderId());
        Provider provider = orderAccess.requireAcceptedProvider(me, order);
        if (!p.getProviderId().equals(provider.getId())) throw BusinessException.forbidden();
        boolean proofFlow = p.getMethod() != null && p.getMethod().usesProof() && p.getStatus() == PaymentStatus.PROOF_SUBMITTED;
        boolean cashFlow = p.getMethod() == PaymentMethod.CASH && p.getStatus() == PaymentStatus.PENDING;
        if (p.getMethod() == null) throw BusinessException.of(ErrorCode.PAYMENT_METHOD_REQUIRED);
        if (!proofFlow && !cashFlow) throw BusinessException.of(ErrorCode.PAYMENT_INVALID_STATE);
        Instant now = clock.instant();
        markConfirmed(p, me.id(), now);
        for (PaymentProof proof : proofRepository.findByPaymentIdOrderByUploadedAtAsc(p.getId())) {
            if (proof.getStatus() == ProofStatus.PENDING_REVIEW) {
                proof.setStatus(ProofStatus.ACCEPTED);
                proof.setReviewedBy(me.id());
                proof.setReviewedAt(now);
            }
        }
        audit.record(me, ActorRole.PROVIDER, AuditAction.PAYMENT_CONFIRMED, "PAYMENT", p.getId(), p.getMethod() + " " + p.getAmount());
        timeline.add(order.getId(), TimelineEvent.PAYMENT_CONFIRMED, me.id(), ActorRole.PROVIDER, p.getAmount() + " " + p.getCurrency());
        notifications.notify(order.getCustomer().getId(), NotificationType.PAYMENT_CONFIRMED, "Payment confirmed",
                "The provider confirmed your payment of " + p.getAmount() + " " + p.getCurrency() + " for order #" + order.getId() + ".", order.getId());
        return view(p);
    }

    void markConfirmed(Payment p, Long by, Instant at) {
        p.setStatus(PaymentStatus.PROVIDER_CONFIRMED);
        p.setAmountPaid(p.getAmount());
        p.setConfirmedAt(at);
        p.setConfirmedBy(by);
        p.setClosedAt(at);
    }

    /** The provider did not receive the money. The customer can upload a new payment or open a dispute. */
    @Transactional
    public PaymentView reject(AuthUser me, Long paymentId, String reason) {
        Payment p = lockPayment(paymentId);
        Order order = orderAccess.loadForUpdate(p.getOrderId());
        Provider provider = orderAccess.requireAcceptedProvider(me, order);
        if (!p.getProviderId().equals(provider.getId())) throw BusinessException.forbidden();
        if (p.getStatus() != PaymentStatus.PROOF_SUBMITTED && !(p.getMethod() == PaymentMethod.CASH && p.getStatus() == PaymentStatus.PENDING)) {
            throw BusinessException.of(ErrorCode.PAYMENT_INVALID_STATE);
        }
        Instant now = clock.instant();
        markRejected(p, reason, me.id(), now);
        audit.record(me, ActorRole.PROVIDER, AuditAction.PAYMENT_REJECTED, "PAYMENT", p.getId(), reason);
        timeline.add(order.getId(), TimelineEvent.PAYMENT_REJECTED, me.id(), ActorRole.PROVIDER, reason);
        notifications.notify(order.getCustomer().getId(), NotificationType.PAYMENT_REJECTED, "Payment not received",
                "The provider reported that the payment for order #" + order.getId() + " did not arrive: " + reason
                        + ". You can pay again or open a dispute.", order.getId());
        return view(p);
    }

    void markRejected(Payment p, String reason, Long by, Instant at) {
        p.setStatus(PaymentStatus.REJECTED);
        p.setRejectedAt(at);
        p.setRejectionReason(reason);
        p.setClosedAt(at);
        for (PaymentProof proof : proofRepository.findByPaymentIdOrderByUploadedAtAsc(p.getId())) {
            if (proof.getStatus() == ProofStatus.PENDING_REVIEW) {
                proof.setStatus(ProofStatus.REJECTED);
                proof.setReviewedBy(by);
                proof.setReviewedAt(at);
                proof.setRejectionReason(reason);
            }
        }
    }

    // ------------------------------------------------------------------ disputes (called by DisputeService)

    @Transactional(propagation = Propagation.MANDATORY)
    public void markDisputed(Payment p) {
        if (p.getStatus() != PaymentStatus.PROOF_SUBMITTED && p.getStatus() != PaymentStatus.REJECTED) {
            throw BusinessException.of(ErrorCode.PAYMENT_INVALID_STATE, "Only a submitted or rejected payment can be disputed");
        }
        // A REJECTED payment re-enters the active set while disputed; a newer active payment must not exist.
        if (p.getStatus() == PaymentStatus.REJECTED
                && paymentRepository.findFirstByOrderIdAndStatusIn(p.getOrderId(), PaymentStatus.ACTIVE).isPresent()) {
            throw BusinessException.of(ErrorCode.PAYMENT_INVALID_STATE, "Cancel the newer open payment before disputing this one");
        }
        p.setStatus(PaymentStatus.DISPUTED);
        p.setDisputedAt(clock.instant());
        p.setClosedAt(null);
    }

    /** Admin decision on a payment dispute. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void resolveDisputed(Payment p, boolean moneyArrived, Long adminId, String note) {
        if (p.getStatus() != PaymentStatus.DISPUTED) throw BusinessException.of(ErrorCode.PAYMENT_INVALID_STATE);
        Instant now = clock.instant();
        if (moneyArrived) {
            markConfirmed(p, adminId, now);
        } else {
            markRejected(p, note == null ? "Rejected after dispute review" : note, adminId, now);
        }
    }

    // ------------------------------------------------------------------ order lifecycle hooks

    /** Order canceled/expired: active payments are canceled (proofs stay as history). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelActive(Order order) {
        paymentRepository.findFirstByOrderIdAndStatusIn(order.getId(), PaymentStatus.ACTIVE).ifPresent(p -> {
            close(p, PaymentStatus.CANCELED);
            audit.system(AuditAction.PAYMENT_CANCELED, "PAYMENT", p.getId(), "order " + order.getStatus());
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Payment> confirmedPayments(Long orderId) {
        return paymentRepository.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                .filter(p -> p.getAmountPaid().signum() > 0).toList();
    }

    private void close(Payment p, PaymentStatus status) {
        p.setStatus(status);
        p.setClosedAt(clock.instant());
    }

    /** Job: PENDING payments with no proof after the TTL expire; the customer can open a new one. */
    @Transactional
    public void expire(Long paymentId) {
        Payment p = lockPayment(paymentId);
        if (p.getStatus() != PaymentStatus.PENDING) return;
        close(p, PaymentStatus.EXPIRED);
        audit.system(AuditAction.PAYMENT_EXPIRED, "PAYMENT", p.getId(), null);
        timeline.system(p.getOrderId(), TimelineEvent.PAYMENT_EXPIRED, null);
        notifications.notify(p.getCustomerId(), NotificationType.PAYMENT_EXPIRED, "Payment request expired",
                "The payment request for order #" + p.getOrderId() + " expired. Select a payment method to pay again.", p.getOrderId());
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public PaymentView get(AuthUser me, Long paymentId) {
        Payment p = paymentRepository.findById(paymentId).orElseThrow(() -> BusinessException.notFound(ErrorCode.PAYMENT_NOT_FOUND));
        orderAccess.requireParty(me, orderAccess.load(p.getOrderId()));
        return view(p);
    }

    @Transactional(readOnly = true)
    public OrderFinancials financials(AuthUser me, Long orderId) {
        Order order = orderAccess.load(orderId);
        orderAccess.requireParty(me, order);
        return financials(order);
    }

    @Transactional(readOnly = true)
    public OrderFinancials financials(Order order) {
        List<PaymentView> payments = paymentRepository.findByOrderIdOrderByCreatedAtAsc(order.getId()).stream().map(this::view).toList();
        List<ChargeView> charges = chargeRepository.findByOrderIdOrderByCreatedAtAsc(order.getId()).stream().map(ChargeView::of).toList();
        List<RefundView> refunds = refundRepository.findByOrderIdOrderByCreatedAtAsc(order.getId()).stream().map(RefundView::of).toList();
        return new OrderFinancials(order.getId(), order.getCurrency(), ledger.acceptedPrice(order), ledger.approvedCharges(order),
                ledger.amountDue(order), ledger.paid(order), ledger.refunded(order), ledger.outstanding(order), payments, charges, refunds);
    }

    @Transactional(readOnly = true)
    public com.doubleclick.wadii.common.api.PageResponse<PaymentView> mine(AuthUser me, Integer page, Integer size) {
        var pageable = com.doubleclick.wadii.common.api.PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = providerAccess.findOwn(me).map(p -> paymentRepository.findByProviderId(p.getId(), pageable))
                .orElseGet(() -> paymentRepository.findByCustomerId(me.id(), pageable));
        return com.doubleclick.wadii.common.api.PageResponse.of(result, this::view);
    }

    @Transactional(readOnly = true)
    public com.doubleclick.wadii.common.api.PageResponse<PaymentView> adminList(PaymentStatus status, Integer page, Integer size) {
        var pageable = com.doubleclick.wadii.common.api.PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = status == null ? paymentRepository.findAll(pageable) : paymentRepository.findByStatus(status, pageable);
        return com.doubleclick.wadii.common.api.PageResponse.of(result, this::view);
    }

    /** Private proof file: the customer, the accepted provider, or support/finance admins. */
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> proofFile(AuthUser me, Long proofId) {
        PaymentProof proof = proofRepository.findById(proofId).orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND));
        Payment p = paymentRepository.findById(proof.getPaymentId()).orElseThrow();
        orderAccess.requireParty(me, orderAccess.load(p.getOrderId()));
        return storage.servePrivate(proof.getFileKey(), proof.getContentType());
    }

    public PaymentView view(Payment p) {
        return PaymentView.of(p, proofRepository.findByPaymentIdOrderByUploadedAtAsc(p.getId()));
    }

    Payment lockPayment(Long id) {
        return paymentRepository.findByIdForUpdate(id).orElseThrow(() -> BusinessException.notFound(ErrorCode.PAYMENT_NOT_FOUND));
    }

    private static String trim(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
