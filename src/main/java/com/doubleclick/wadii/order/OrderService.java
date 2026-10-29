package com.doubleclick.wadii.order;

import com.doubleclick.wadii.appointment.AppointmentRepository;
import com.doubleclick.wadii.appointment.AppointmentService;
import com.doubleclick.wadii.audit.AuditAction;
import com.doubleclick.wadii.audit.AuditService;
import com.doubleclick.wadii.catalog.CarTypeRepository;
import com.doubleclick.wadii.catalog.CatalogService;
import com.doubleclick.wadii.catalog.CatalogServiceRepository;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.config.WadiiProperties;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.identity.User;
import com.doubleclick.wadii.identity.UserRepository;
import com.doubleclick.wadii.notification.NotificationService;
import com.doubleclick.wadii.notification.NotificationType;
import com.doubleclick.wadii.order.OrderCancellationService.RefundRule;
import com.doubleclick.wadii.order.dto.OrderDtos.*;
import com.doubleclick.wadii.order.matching.MatchingService;
import com.doubleclick.wadii.payment.InvoiceService;
import com.doubleclick.wadii.payment.PaymentLedger;
import com.doubleclick.wadii.payment.RefundSource;
import com.doubleclick.wadii.policy.PolicyService;
import com.doubleclick.wadii.policy.PolicyType;
import com.doubleclick.wadii.provider.Provider;
import com.doubleclick.wadii.provider.ProviderAccess;
import com.doubleclick.wadii.response.OrderResponseRepository;
import com.doubleclick.wadii.storage.FileCategory;
import com.doubleclick.wadii.storage.FileStorageService;
import com.doubleclick.wadii.storage.StoredFile;
import com.doubleclick.wadii.warranty.WarrantyService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final int MAX_ATTACHMENTS = 10;

    private final OrderRepository orderRepository;
    private final OrderProviderTargetRepository targetRepository;
    private final OrderAttachmentRepository attachmentRepository;
    private final OrderTimelineRepository timelineRepository;
    private final OrderCancellationRepository cancellationRepository;
    private final OrderResponseRepository responseRepository;
    private final CatalogServiceRepository catalogServiceRepository;
    private final CarTypeRepository carTypeRepository;
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final MatchingService matchingService;
    private final OrderAccess orderAccess;
    private final ProviderAccess providerAccess;
    private final OrderCancellationService cancellationService;
    private final AppointmentService appointmentService;
    private final PaymentLedger ledger;
    private final InvoiceService invoiceService;
    private final WarrantyService warrantyService;
    private final PolicyService policyService;
    private final FileStorageService storage;
    private final NotificationService notifications;
    private final AuditService audit;
    private final OrderTimelineService timeline;
    private final WadiiProperties properties;
    private final Clock clock;

    // ------------------------------------------------------------------ create

    @Transactional
    public OrderView create(AuthUser me, CreateOrderRequest r) {
        if (me.isAdmin()) throw BusinessException.forbidden("Admin accounts cannot create orders");
        LocalDate today = LocalDate.now(clock.withZone(properties.zone()));
        if (r.requestedDate().isBefore(today)) {
            throw BusinessException.of(ErrorCode.VALIDATION_FAILED, "requestedDate cannot be in the past");
        }
        if ((r.latitude() == null) != (r.longitude() == null)) {
            throw BusinessException.validation("latitude and longitude must be sent together");
        }
        Set<Long> serviceIds = new HashSet<>(r.serviceIds());
        List<CatalogService> services = catalogServiceRepository.findAllById(serviceIds);
        if (services.size() != serviceIds.size() || services.stream().anyMatch(s -> !s.isActive())) {
            throw BusinessException.validation("One or more service ids do not exist or are inactive");
        }
        User customer = userRepository.findById(me.id()).orElseThrow();
        Provider explicit = r.providerId() == null ? null : matchingService.validateExplicit(r.providerId(), me.id(), serviceIds);

        Order o = new Order();
        o.setCustomer(customer);
        o.setCarType(r.carTypeId() == null ? null
                : carTypeRepository.findById(r.carTypeId()).orElseThrow(() -> BusinessException.validation("carTypeId does not exist")));
        o.setCarDescription(r.carDescription().trim());
        o.setRequestedDate(r.requestedDate());
        o.setAddress(r.address());
        o.setLatitude(r.latitude());
        o.setLongitude(r.longitude());
        o.setComment(r.comment());
        o.setSelectedProvider(explicit);
        o.setCurrency(properties.getCurrency());
        o.setResponseDeadline(clock.instant().plus(Duration.ofHours(properties.getOrder().getResponseWindowHours())));
        o.getServices().addAll(services);
        if (r.spareParts() != null) {
            for (String name : r.spareParts()) {
                OrderSparePart part = new OrderSparePart();
                part.setOrder(o);
                part.setName(name.trim());
                o.getSpareParts().add(part);
            }
        }
        o = orderRepository.save(o);
        timeline.add(o.getId(), TimelineEvent.ORDER_CREATED, me.id(), ActorRole.CUSTOMER,
                explicit == null ? "automatic matching" : "selected provider " + explicit.getId());
        audit.record(me, ActorRole.CUSTOMER, AuditAction.ORDER_CREATED, "ORDER", o.getId(), null);

        Instant now = clock.instant();
        if (explicit != null) {
            matchingService.target(o, List.of(matchingService.explicitCandidate(o, explicit)));
            OrderStateMachine.transition(o, OrderStatus.QUOTING, now);
        } else {
            OrderStateMachine.transition(o, OrderStatus.MATCHING, now);
            int targeted = matchingService.target(o, matchingService.candidates(o));
            if (targeted > 0) {
                OrderStateMachine.transition(o, OrderStatus.QUOTING, now);
            } else {
                timeline.system(o.getId(), TimelineEvent.NO_PROVIDER_MATCHED, "will retry until the response deadline");
            }
        }
        notifications.notify(me.id(), NotificationType.ORDER_CREATED, "Order created",
                o.getStatus() == OrderStatus.QUOTING ? "Order #" + o.getId() + " was sent to providers."
                        : "Order #" + o.getId() + " was created. We are still looking for matching providers.", o.getId());
        return toView(me, o, OrderAccess.Relation.CUSTOMER, null);
    }

    /** Job: retries matching for orders that found no provider yet. */
    @Transactional
    public void rematch(Long orderId) {
        Order o = orderAccess.loadForUpdate(orderId);
        if (o.getStatus() != OrderStatus.MATCHING || !clock.instant().isBefore(o.getResponseDeadline())) return;
        if (matchingService.target(o, matchingService.candidates(o)) > 0) {
            OrderStateMachine.transition(o, OrderStatus.QUOTING, clock.instant());
        }
    }

    // ------------------------------------------------------------------ read

    @Transactional
    public OrderView get(AuthUser me, Long orderId) {
        Order o = orderAccess.load(orderId);
        OrderAccess.Access access = orderAccess.view(me, o);
        if (access.relation() == OrderAccess.Relation.TARGETED_PROVIDER && access.target().getStatus() == TargetStatus.TARGETED) {
            access.target().setStatus(TargetStatus.VIEWED);
            access.target().setViewedAt(clock.instant());
        }
        return toView(me, o, access.relation(), access.target());
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderView> mine(AuthUser me, Set<OrderStatus> statuses, Integer page, Integer size) {
        var pageable = PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = statuses == null || statuses.isEmpty()
                ? orderRepository.findByCustomerId(me.id(), pageable)
                : orderRepository.findByCustomerIdAndStatusIn(me.id(), statuses, pageable);
        return PageResponse.of(result, o -> toView(me, o, OrderAccess.Relation.CUSTOMER, null));
    }

    /** Orders the provider was invited to. scope: OPEN (can still quote), ASSIGNED (won, in progress), CLOSED, ALL. */
    @Transactional(readOnly = true)
    public PageResponse<OrderView> forProvider(AuthUser me, String scope, Integer page, Integer size) {
        Provider p = providerAccess.requireOwn(me);
        Set<OrderStatus> statuses = switch (scope == null ? "ALL" : scope.toUpperCase()) {
            case "OPEN" -> EnumSet.of(OrderStatus.QUOTING, OrderStatus.RESPONSES_AVAILABLE);
            case "ASSIGNED" -> EnumSet.of(OrderStatus.ACCEPTED, OrderStatus.IN_PROGRESS);
            case "CLOSED" -> EnumSet.of(OrderStatus.COMPLETED, OrderStatus.CANCELED, OrderStatus.EXPIRED);
            default -> EnumSet.allOf(OrderStatus.class);
        };
        var result = targetRepository.findForProvider(p.getId(), statuses, PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.of(result, t -> {
            Order o = t.getOrder();
            OrderAccess.Relation rel = o.isAcceptedProvider(p.getId()) ? OrderAccess.Relation.ACCEPTED_PROVIDER : OrderAccess.Relation.TARGETED_PROVIDER;
            return toView(me, o, rel, t);
        });
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderView> adminList(Set<OrderStatus> statuses, Integer page, Integer size) {
        var pageable = PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = orderRepository.findByStatusIn(statuses == null || statuses.isEmpty() ? EnumSet.allOf(OrderStatus.class) : statuses, pageable);
        return PageResponse.of(result, o -> toView(null, o, OrderAccess.Relation.ADMIN, null));
    }

    @Transactional(readOnly = true)
    public PageResponse<TimelineView> timeline(AuthUser me, Long orderId, Integer page, Integer size) {
        Order o = orderAccess.load(orderId);
        OrderAccess.Relation rel = orderAccess.view(me, o).relation();
        if (rel == OrderAccess.Relation.TARGETED_PROVIDER) throw BusinessException.forbidden("Only the parties of the order can read its timeline");
        return PageResponse.of(timelineRepository.findByOrderId(orderId, PageResponse.pageable(page, size, Sort.by("createdAt", "id"))), TimelineView::of);
    }

    @Transactional(readOnly = true)
    public CancellationView cancellation(AuthUser me, Long orderId) {
        Order o = orderAccess.load(orderId);
        orderAccess.view(me, o);
        return cancellationRepository.findByOrderId(orderId).map(CancellationView::of)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<PolicyService.SnapshotView> policies(AuthUser me, Long orderId) {
        Order o = orderAccess.load(orderId);
        orderAccess.view(me, o);
        return policyService.snapshotsOf(orderId);
    }

    // ------------------------------------------------------------------ update (before any offer)

    @Transactional
    public OrderView update(AuthUser me, Long orderId, UpdateOrderRequest r) {
        Order o = orderAccess.loadForUpdate(orderId);
        orderAccess.requireCustomer(me, o);
        if (!o.getStatus().awaitingOffers() || responseRepository.findByOrderId(orderId).size() > 0) {
            throw BusinessException.of(ErrorCode.ORDER_INVALID_STATE, "An order can only be edited before any offer is received");
        }
        if (r.carDescription() != null) {
            if (r.carDescription().isBlank()) throw BusinessException.validation("carDescription must not be blank");
            o.setCarDescription(r.carDescription().trim());
        }
        if (r.requestedDate() != null) {
            if (r.requestedDate().isBefore(LocalDate.now(clock.withZone(properties.zone())))) {
                throw BusinessException.validation("requestedDate cannot be in the past");
            }
            o.setRequestedDate(r.requestedDate());
        }
        if (r.address() != null) o.setAddress(r.address());
        if (r.comment() != null) o.setComment(r.comment());
        return toView(me, o, OrderAccess.Relation.CUSTOMER, null);
    }

    // ------------------------------------------------------------------ cancel

    /**
     * Customer: any time before IN_PROGRESS (after acceptance the CANCELLATION policy snapshot decides the fee).
     * Accepted provider: while ACCEPTED (full refund of anything paid).
     * Support admin: any non-terminal order (full refund).
     * IN_PROGRESS orders can only be canceled by support; the customer should open a dispute.
     */
    @Transactional
    public CancelResult cancel(AuthUser me, Long orderId, CancelRequest request) {
        Order o = orderAccess.loadForUpdate(orderId);
        if (o.getStatus().isTerminal()) throw BusinessException.of(ErrorCode.ORDER_INVALID_STATE, "The order is already " + o.getStatus());
        List<Long> refunds;
        String reasonText = request == null ? null : request.reason();
        Long reasonId = request == null ? null : request.reasonId();
        if (o.isOwnedBy(me.id())) {
            if (o.getStatus() == OrderStatus.IN_PROGRESS) {
                throw BusinessException.of(ErrorCode.ORDER_INVALID_STATE, "The service already started; open a dispute instead");
            }
            refunds = cancellationService.cancel(o, me, ActorRole.CUSTOMER, reasonId, reasonText, customerRefundRule(o), RefundSource.CANCELLATION);
        } else if (providerAccess.findOwn(me).map(p -> o.isAcceptedProvider(p.getId())).orElse(false)) {
            if (o.getStatus() != OrderStatus.ACCEPTED) {
                throw BusinessException.of(ErrorCode.ORDER_INVALID_STATE, "A provider can only cancel before starting the service");
            }
            if (reasonText == null && reasonId == null) throw BusinessException.validation("A reason is required");
            refunds = cancellationService.cancel(o, me, ActorRole.PROVIDER, reasonId, reasonText, RefundRule.full(), RefundSource.CANCELLATION);
        } else if (me.isSupport()) {
            if (reasonText == null && reasonId == null) throw BusinessException.validation("A reason is required");
            refunds = cancellationService.cancel(o, me, ActorRole.ADMIN, reasonId, reasonText, RefundRule.full(), RefundSource.ADMIN);
        } else {
            throw BusinessException.forbidden("You cannot cancel this order");
        }
        return new CancelResult(toView(me, o, OrderAccess.Relation.CUSTOMER, null), refunds);
    }

    /** Free before acceptance; afterwards the snapshot fee applies inside the free-cancellation window rule. */
    private RefundRule customerRefundRule(Order o) {
        if (o.getStatus() != OrderStatus.ACCEPTED) return RefundRule.full();
        PolicyService.PolicyTerms terms = policyService.snapshotTerms(o.getId(), PolicyType.CANCELLATION);
        BigDecimal fee = terms.feePercent() == null ? BigDecimal.ZERO : terms.feePercent();
        if (fee.signum() == 0) return RefundRule.full();
        var appointment = appointmentRepository.findByOrderId(o.getId()).filter(a -> a.getStatus().isOpen());
        if (appointment.isEmpty()) return RefundRule.full();
        int freeHours = terms.freeCancellationHours() == null ? 0 : terms.freeCancellationHours();
        boolean late = clock.instant().isAfter(appointment.get().getScheduledStart().minus(Duration.ofHours(freeHours)));
        return late ? RefundRule.minusFee(fee) : RefundRule.full();
    }

    /** A targeted provider declines to quote. */
    @Transactional
    public void declineInvitation(AuthUser me, Long orderId) {
        Provider p = providerAccess.requireOwn(me);
        OrderProviderTarget t = targetRepository.findByOrderIdAndProviderId(orderId, p.getId())
                .orElseThrow(() -> BusinessException.of(ErrorCode.RESPONSE_NOT_TARGETED));
        if (t.getStatus() != TargetStatus.TARGETED && t.getStatus() != TargetStatus.VIEWED) {
            throw BusinessException.of(ErrorCode.CONFLICT, "This invitation is already " + t.getStatus());
        }
        t.setStatus(TargetStatus.DECLINED);
    }

    // ------------------------------------------------------------------ service execution

    /** Accepted provider starts the job. V1 rule PAY_BEFORE_SERVICE: the order must be fully paid. */
    @Transactional
    public OrderView start(AuthUser me, Long orderId) {
        Order o = orderAccess.loadForUpdate(orderId);
        orderAccess.requireAcceptedProvider(me, o);
        if (o.getStatus() != OrderStatus.ACCEPTED) throw BusinessException.of(ErrorCode.ORDER_INVALID_STATE);
        if (!ledger.isFullyPaid(o)) throw BusinessException.of(ErrorCode.ORDER_NOT_FULLY_PAID);
        OrderStateMachine.transition(o, OrderStatus.IN_PROGRESS, clock.instant());
        timeline.add(orderId, TimelineEvent.SERVICE_STARTED, me.id(), ActorRole.PROVIDER, null);
        audit.record(me, ActorRole.PROVIDER, AuditAction.ORDER_STARTED, "ORDER", orderId, null);
        notifications.notify(o.getCustomer().getId(), NotificationType.ORDER_STARTED, "Service started",
                "The provider started working on order #" + orderId + ".", orderId);
        return toView(me, o, OrderAccess.Relation.ACCEPTED_PROVIDER, null);
    }

    @Transactional
    public OrderView markDone(AuthUser me, Long orderId) {
        Order o = orderAccess.loadForUpdate(orderId);
        orderAccess.requireAcceptedProvider(me, o);
        if (o.getStatus() != OrderStatus.IN_PROGRESS || o.getProviderCompletedAt() != null) {
            throw BusinessException.of(ErrorCode.ORDER_INVALID_STATE);
        }
        o.setProviderCompletedAt(clock.instant());
        timeline.add(orderId, TimelineEvent.SERVICE_MARKED_DONE, me.id(), ActorRole.PROVIDER, null);
        int hours = properties.getOrder().getAutoCompleteHours();
        notifications.notify(o.getCustomer().getId(), NotificationType.SERVICE_MARKED_DONE, "Service done",
                "The provider marked order #" + orderId + " as done. Please confirm"
                        + (hours > 0 ? " (it completes automatically after " + hours + " hours)." : "."), orderId);
        return toView(me, o, OrderAccess.Relation.ACCEPTED_PROVIDER, null);
    }

    @Transactional
    public OrderView confirmCompletion(AuthUser me, Long orderId) {
        Order o = orderAccess.loadForUpdate(orderId);
        orderAccess.requireCustomer(me, o);
        if (o.getStatus() != OrderStatus.IN_PROGRESS || o.getProviderCompletedAt() == null) {
            throw BusinessException.of(ErrorCode.ORDER_INVALID_STATE, "The provider has not marked the service as done yet");
        }
        complete(o, me, ActorRole.CUSTOMER);
        return toView(me, o, OrderAccess.Relation.CUSTOMER, null);
    }

    /** Job: auto-confirms after the configured timeout, only when nothing is outstanding. */
    @Transactional
    public void autoComplete(Long orderId) {
        Order o = orderAccess.loadForUpdate(orderId);
        if (o.getStatus() != OrderStatus.IN_PROGRESS || o.getProviderCompletedAt() == null) return;
        if (ledger.outstanding(o).signum() > 0) return;
        complete(o, null, ActorRole.SYSTEM);
    }

    private void complete(Order o, AuthUser actor, ActorRole role) {
        if (ledger.outstanding(o).signum() > 0) throw BusinessException.of(ErrorCode.ORDER_NOT_FULLY_PAID);
        OrderStateMachine.transition(o, OrderStatus.COMPLETED, clock.instant());
        appointmentService.completeFor(o.getId());
        invoiceService.issue(o);
        warrantyService.issueFor(o);
        timeline.add(o.getId(), TimelineEvent.SERVICE_COMPLETED, actor == null ? null : actor.id(), role, null);
        if (actor == null) audit.system(AuditAction.ORDER_COMPLETED, "ORDER", o.getId(), "auto-completed");
        else audit.record(actor, role, AuditAction.ORDER_COMPLETED, "ORDER", o.getId(), null);
        notifications.notify(o.getAcceptedProvider().getUser().getId(), NotificationType.ORDER_COMPLETED, "Order completed",
                "Order #" + o.getId() + " is completed.", o.getId());
        notifications.notify(o.getCustomer().getId(), NotificationType.ORDER_COMPLETED, "Order completed",
                "Order #" + o.getId() + " is completed. You can now rate the provider.", o.getId());
    }

    // ------------------------------------------------------------------ attachments

    @Transactional
    public AttachmentView addAttachment(AuthUser me, Long orderId, MultipartFile file) {
        Order o = orderAccess.load(orderId);
        orderAccess.requireCustomer(me, o);
        if (o.getStatus().isTerminal()) throw BusinessException.of(ErrorCode.ORDER_INVALID_STATE);
        if (attachmentRepository.countByOrderId(orderId) >= MAX_ATTACHMENTS) {
            throw BusinessException.validation("At most " + MAX_ATTACHMENTS + " attachments per order");
        }
        StoredFile stored = storage.store(file, FileCategory.ORDER_ATTACHMENT);
        OrderAttachment a = new OrderAttachment();
        a.setOrderId(orderId);
        a.setUploadedBy(me.id());
        a.setFileKey(stored.key());
        a.setContentType(stored.contentType());
        a.setSizeBytes(stored.size());
        return AttachmentView.of(attachmentRepository.save(a));
    }

    @Transactional(readOnly = true)
    public List<AttachmentView> attachments(AuthUser me, Long orderId) {
        orderAccess.view(me, orderAccess.load(orderId));
        return attachmentRepository.findByOrderIdOrderById(orderId).stream().map(AttachmentView::of).toList();
    }

    /** Customer, targeted / accepted providers and support only. */
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> attachmentFile(AuthUser me, Long orderId, Long attachmentId) {
        orderAccess.view(me, orderAccess.load(orderId));
        OrderAttachment a = attachmentRepository.findById(attachmentId)
                .filter(x -> x.getOrderId().equals(orderId))
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND));
        return storage.servePrivate(a.getFileKey(), a.getContentType());
    }

    // ------------------------------------------------------------------ mapping

    OrderView toView(AuthUser me, Order o, OrderAccess.Relation relation, OrderProviderTarget target) {
        CounterpartView customer = null;
        if (relation == OrderAccess.Relation.CUSTOMER || relation == OrderAccess.Relation.ADMIN || relation == OrderAccess.Relation.ACCEPTED_PROVIDER) {
            // Targeted (not yet chosen) providers never see who the customer is.
            User c = o.getCustomer();
            customer = new CounterpartView(c.getId(), c.getFirstName(), c.getLastName(), c.getImage(), c.getPhone());
        }
        return com.doubleclick.wadii.order.dto.OrderDtos.view(o, relation.name(), customer, target == null ? null : target.getStatus(),
                target == null ? null : target.getDistanceKm());
    }
}
