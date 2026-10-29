package com.doubleclick.wadii.response;

import com.doubleclick.wadii.appointment.AppointmentService;
import com.doubleclick.wadii.audit.AuditAction;
import com.doubleclick.wadii.audit.AuditService;
import com.doubleclick.wadii.chat.ConversationService;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.config.WadiiProperties;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.geo.Geo;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.notification.NotificationService;
import com.doubleclick.wadii.notification.NotificationType;
import com.doubleclick.wadii.order.ActorRole;
import com.doubleclick.wadii.order.Order;
import com.doubleclick.wadii.order.OrderAccess;
import com.doubleclick.wadii.order.OrderProviderTarget;
import com.doubleclick.wadii.order.OrderProviderTargetRepository;
import com.doubleclick.wadii.order.OrderSparePart;
import com.doubleclick.wadii.order.OrderStateMachine;
import com.doubleclick.wadii.order.OrderStatus;
import com.doubleclick.wadii.order.OrderTimelineService;
import com.doubleclick.wadii.order.TargetStatus;
import com.doubleclick.wadii.order.TimelineEvent;
import com.doubleclick.wadii.order.matching.RankingService;
import com.doubleclick.wadii.payment.Payment;
import com.doubleclick.wadii.payment.PaymentService;
import com.doubleclick.wadii.policy.PolicyService;
import com.doubleclick.wadii.provider.Provider;
import com.doubleclick.wadii.provider.ProviderAccess;
import com.doubleclick.wadii.provider.ProviderBranch;
import com.doubleclick.wadii.provider.ProviderBranchRepository;
import com.doubleclick.wadii.provider.ProviderStats;
import com.doubleclick.wadii.provider.ProviderStatsRepository;
import com.doubleclick.wadii.response.dto.ResponseDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Provider offers on orders: submit, update, withdraw, customer decline, and the atomic accept. */
@Service
@RequiredArgsConstructor
public class ResponseService {

    private final OrderResponseRepository responseRepository;
    private final OrderProviderTargetRepository targetRepository;
    private final ProviderBranchRepository branchRepository;
    private final ProviderStatsRepository statsRepository;
    private final OrderAccess orderAccess;
    private final ProviderAccess providerAccess;
    private final RankingService rankingService;
    private final PolicyService policyService;
    private final PaymentService paymentService;
    private final ConversationService conversationService;
    private final AppointmentService appointmentService;
    private final NotificationService notifications;
    private final AuditService audit;
    private final OrderTimelineService timeline;
    private final WadiiProperties properties;
    private final Clock clock;

    // ------------------------------------------------------------------ provider side

    @Transactional
    public ResponseView submit(AuthUser me, SubmitResponseRequest r) {
        Provider provider = providerAccess.requireOwn(me);
        // Lock the order: no offer can slip in while the customer accepts another one or the deadline job runs.
        Order order = orderAccess.loadForUpdate(r.orderId());
        if (!provider.isEligibleForOrders()) throw BusinessException.of(ErrorCode.PROVIDER_NOT_ELIGIBLE);
        OrderProviderTarget target = targetRepository.findByOrderIdAndProviderId(order.getId(), provider.getId())
                .orElseThrow(() -> BusinessException.of(ErrorCode.RESPONSE_NOT_TARGETED));
        if (target.getStatus() == TargetStatus.DECLINED || target.getStatus() == TargetStatus.EXPIRED) {
            throw BusinessException.of(ErrorCode.RESPONSE_NOT_TARGETED, "Your invitation for this order is " + target.getStatus());
        }
        Instant now = clock.instant();
        if (!order.getStatus().acceptsOffers()) throw BusinessException.of(ErrorCode.ORDER_INVALID_STATE, "The order is " + order.getStatus());
        if (!now.isBefore(order.getResponseDeadline())) throw BusinessException.of(ErrorCode.ORDER_DEADLINE_PASSED);
        if (responseRepository.existsByOrderIdAndProviderId(order.getId(), provider.getId())) {
            throw BusinessException.of(ErrorCode.RESPONSE_ALREADY_EXISTS);
        }
        OrderResponse resp = new OrderResponse();
        resp.setOrder(order);
        resp.setProvider(provider);
        resp.setCurrency(order.getCurrency());
        resp.setDescription(r.description().trim());
        resp.setEstimatedDurationMinutes(r.estimatedDurationMinutes());
        resp.setBranch(resolveBranch(provider, r.branchId(), target));
        applyPricing(resp, order, r.laborCost(), r.partsCost(), r.discount(), r.partPrices());
        resp.setExpiresAt(resolveExpiry(order, r.expiresAt(), now));
        applyProposedTime(resp, r.proposedAppointmentAt());
        resp = responseRepository.save(resp);

        target.setStatus(TargetStatus.RESPONDED);
        target.setRespondedAt(now);
        if (order.getStatus() == OrderStatus.QUOTING) {
            OrderStateMachine.transition(order, OrderStatus.RESPONSES_AVAILABLE, now);
        }
        timeline.add(order.getId(), TimelineEvent.OFFER_SUBMITTED, me.id(), ActorRole.PROVIDER, "provider " + provider.getId() + ": " + resp.getPrice());
        audit.record(me, ActorRole.PROVIDER, AuditAction.RESPONSE_CREATED, "RESPONSE", resp.getId(), "order " + order.getId());
        notifications.notify(order.getCustomer().getId(), NotificationType.NEW_RESPONSE, "New offer",
                provider.getName() + " sent an offer of " + resp.getPrice() + " " + resp.getCurrency() + " for order #" + order.getId() + ".", order.getId());
        return ResponseView.of(resp, target.getDistanceKm());
    }

    /** A pending, unexpired offer may be revised before the customer accepts. Accepted prices never change. */
    @Transactional
    public ResponseView update(AuthUser me, Long responseId, UpdateResponseRequest r) {
        Provider provider = providerAccess.requireOwn(me);
        OrderResponse existing = load(responseId);
        if (!existing.getProvider().getId().equals(provider.getId())) throw BusinessException.forbidden("This offer belongs to another provider");
        Order order = orderAccess.loadForUpdate(existing.getOrder().getId());
        OrderResponse resp = load(responseId);
        Instant now = clock.instant();
        if (resp.getStatus() != ResponseStatus.PENDING) throw BusinessException.of(ErrorCode.RESPONSE_NOT_PENDING);
        if (resp.isExpiredAt(now)) throw BusinessException.of(ErrorCode.OFFER_EXPIRED);
        if (!order.getStatus().acceptsOffers() || !now.isBefore(order.getResponseDeadline())) throw BusinessException.of(ErrorCode.ORDER_DEADLINE_PASSED);
        if (r.description() != null) {
            if (r.description().isBlank()) throw BusinessException.validation("description must not be blank");
            resp.setDescription(r.description().trim());
        }
        if (r.estimatedDurationMinutes() != null) resp.setEstimatedDurationMinutes(r.estimatedDurationMinutes());
        if (r.branchId() != null) resp.setBranch(resolveBranch(provider, r.branchId(), null));
        if (r.laborCost() != null || r.partsCost() != null || r.discount() != null || r.partPrices() != null) {
            applyPricing(resp, order,
                    r.laborCost() != null ? r.laborCost() : resp.getLaborCost(),
                    r.partPrices() == null && r.partsCost() == null ? resp.getPartsCost() : r.partsCost(),
                    r.discount() != null ? r.discount() : resp.getDiscount(),
                    r.partPrices());
        }
        if (r.expiresAt() != null) resp.setExpiresAt(resolveExpiry(order, r.expiresAt(), now));
        if (r.proposedAppointmentAt() != null) applyProposedTime(resp, r.proposedAppointmentAt());
        timeline.add(order.getId(), TimelineEvent.OFFER_UPDATED, me.id(), ActorRole.PROVIDER, "offer " + resp.getId() + ": " + resp.getPrice());
        audit.record(me, ActorRole.PROVIDER, AuditAction.RESPONSE_UPDATED, "RESPONSE", resp.getId(), resp.getPrice().toPlainString());
        notifications.notify(order.getCustomer().getId(), NotificationType.NEW_RESPONSE, "Offer updated",
                provider.getName() + " updated its offer on order #" + order.getId() + " to " + resp.getPrice() + " " + resp.getCurrency() + ".", order.getId());
        return ResponseView.of(resp, distance(order, resp));
    }

    /** Provider withdraws a pending offer. It is kept as history (CANCELED). */
    @Transactional
    public ResponseView withdraw(AuthUser me, Long responseId) {
        Provider provider = providerAccess.requireOwn(me);
        OrderResponse existing = load(responseId);
        if (!existing.getProvider().getId().equals(provider.getId())) throw BusinessException.forbidden("This offer belongs to another provider");
        Order order = orderAccess.loadForUpdate(existing.getOrder().getId());
        OrderResponse resp = load(responseId);
        if (resp.getStatus() != ResponseStatus.PENDING) throw BusinessException.of(ErrorCode.RESPONSE_NOT_PENDING);
        resp.changeStatus(ResponseStatus.CANCELED, clock.instant());
        fallBackToQuoting(order);
        timeline.add(order.getId(), TimelineEvent.OFFER_WITHDRAWN, me.id(), ActorRole.PROVIDER, "offer " + resp.getId());
        audit.record(me, ActorRole.PROVIDER, AuditAction.RESPONSE_WITHDRAWN, "RESPONSE", resp.getId(), null);
        return ResponseView.of(resp, distance(order, resp));
    }

    // ------------------------------------------------------------------ customer side

    /** Customer declines one pending offer. Kept as history (DECLINED). */
    @Transactional
    public ResponseView decline(AuthUser me, Long responseId) {
        OrderResponse existing = load(responseId);
        Order order = orderAccess.loadForUpdate(existing.getOrder().getId());
        orderAccess.requireCustomer(me, order);
        OrderResponse resp = load(responseId);
        if (resp.getStatus() != ResponseStatus.PENDING || !order.getStatus().acceptsOffers()) {
            throw BusinessException.of(ErrorCode.RESPONSE_NOT_PENDING);
        }
        resp.changeStatus(ResponseStatus.DECLINED, clock.instant());
        fallBackToQuoting(order);
        timeline.add(order.getId(), TimelineEvent.OFFER_DECLINED, me.id(), ActorRole.CUSTOMER, "offer " + resp.getId());
        audit.record(me, ActorRole.CUSTOMER, AuditAction.RESPONSE_DECLINED, "RESPONSE", resp.getId(), null);
        notifications.notify(resp.getProvider().getUser().getId(), NotificationType.RESPONSE_DECLINED, "Offer declined",
                "Your offer for order #" + order.getId() + " was declined.", order.getId());
        return ResponseView.of(resp, distance(order, resp));
    }

    /**
     * Atomic acceptance, in one transaction under a row lock on the order:
     * verify ownership, lock, verify the offer belongs to the order, is PENDING and unexpired, accept it,
     * decline every other pending offer (kept as history), set acceptedProvider/acceptedResponse, order = ACCEPTED,
     * snapshot policies, open the conversation and the payment, create the appointment if one was proposed, notify.
     * The partial unique index {@code uq_response_one_accepted_per_order} is the last line of defence.
     */
    @Transactional
    public AcceptResult accept(AuthUser me, Long responseId, Long expectedOrderId) {
        OrderResponse probe = load(responseId);
        Long orderId = probe.getOrder().getId();
        if (expectedOrderId != null && !expectedOrderId.equals(orderId)) throw BusinessException.of(ErrorCode.RESPONSE_ORDER_MISMATCH);
        Order order = orderAccess.loadForUpdate(orderId);
        orderAccess.requireCustomer(me, order);
        Instant now = clock.instant();
        if (order.getAcceptedResponseId() != null) throw BusinessException.of(ErrorCode.RESPONSE_ALREADY_ACCEPTED);
        if (!order.getStatus().acceptsOffers()) throw BusinessException.of(ErrorCode.ORDER_INVALID_STATE, "The order is " + order.getStatus());
        if (!now.isBefore(order.getResponseDeadline())) throw BusinessException.of(ErrorCode.ORDER_DEADLINE_PASSED);

        List<OrderResponse> all = responseRepository.findByOrderId(orderId);
        OrderResponse chosen = all.stream().filter(x -> x.getId().equals(responseId)).findFirst()
                .orElseThrow(() -> BusinessException.of(ErrorCode.RESPONSE_ORDER_MISMATCH));
        if (all.stream().anyMatch(x -> x.getStatus() == ResponseStatus.ACCEPTED)) throw BusinessException.of(ErrorCode.RESPONSE_ALREADY_ACCEPTED);
        if (chosen.getStatus() != ResponseStatus.PENDING) {
            throw BusinessException.of(chosen.getStatus() == ResponseStatus.EXPIRED ? ErrorCode.OFFER_EXPIRED : ErrorCode.RESPONSE_NOT_PENDING);
        }
        if (chosen.isExpiredAt(now)) throw BusinessException.of(ErrorCode.OFFER_EXPIRED);
        if (!chosen.getProvider().isEligibleForOrders()) throw BusinessException.of(ErrorCode.PROVIDER_NOT_ELIGIBLE);

        chosen.changeStatus(ResponseStatus.ACCEPTED, now);
        for (OrderResponse other : all) {
            if (!other.getId().equals(chosen.getId()) && other.getStatus() == ResponseStatus.PENDING) {
                other.changeStatus(ResponseStatus.DECLINED, now);
                notifications.notify(other.getProvider().getUser().getId(), NotificationType.RESPONSE_DECLINED, "Offer not selected",
                        "The customer chose another offer for order #" + orderId + ".", orderId);
            }
        }
        targetRepository.expireOpenTargets(orderId, now);
        OrderStateMachine.accept(order, chosen.getProvider(), chosen.getId(), now);
        responseRepository.flush(); // surfaces a unique-index violation inside this transaction

        policyService.snapshot(orderId, chosen.getProvider().getId());
        timeline.system(orderId, TimelineEvent.POLICY_SNAPSHOT_CREATED, null);
        Long conversationId = conversationService.openForOrder(order).getId();
        appointmentService.createFromOffer(order, chosen);
        Payment payment = paymentService.openForOutstanding(order).orElse(null);

        timeline.add(orderId, TimelineEvent.OFFER_ACCEPTED, me.id(), ActorRole.CUSTOMER, "offer " + chosen.getId() + " from provider " + chosen.getProvider().getId());
        audit.record(me, ActorRole.CUSTOMER, AuditAction.RESPONSE_ACCEPTED, "RESPONSE", chosen.getId(), "order " + orderId + " price " + chosen.getPrice());
        notifications.notify(chosen.getProvider().getUser().getId(), NotificationType.RESPONSE_ACCEPTED, "Offer accepted",
                "Your offer for order #" + orderId + " was accepted.", orderId);
        return new AcceptResult(ResponseView.of(chosen, distance(order, chosen)), orderId, payment == null ? null : payment.getId(), conversationId);
    }

    // ------------------------------------------------------------------ reads

    /** All offers of an order for the customer (or support), sorted. Bounded by max-targets, paged in memory. */
    @Transactional(readOnly = true)
    public PageResponse<ResponseView> forOrder(AuthUser me, Long orderId, SortMode sort, Integer page, Integer size) {
        Order order = orderAccess.load(orderId);
        OrderAccess.Relation rel = orderAccess.view(me, order).relation();
        if (rel != OrderAccess.Relation.CUSTOMER && rel != OrderAccess.Relation.ADMIN) {
            throw BusinessException.forbidden("Providers only see their own offer");
        }
        List<OrderResponse> all = new ArrayList<>(responseRepository.findByOrderId(orderId));
        Map<Long, BigDecimal> distances = new HashMap<>();
        all.forEach(x -> distances.put(x.getId(), distance(order, x)));
        all.sort(comparator(sort == null ? SortMode.RECOMMENDED : sort, order, distances, all));
        var pageable = PageResponse.pageable(page, size);
        int from = (int) Math.min(pageable.getOffset(), all.size());
        int to = Math.min(from + pageable.getPageSize(), all.size());
        List<ResponseView> items = all.subList(from, to).stream().map(x -> ResponseView.of(x, distances.get(x.getId()))).toList();
        return PageResponse.of(new PageImpl<>(items, pageable, all.size()));
    }

    @Transactional(readOnly = true)
    public ResponseView mineForOrder(AuthUser me, Long orderId) {
        Provider provider = providerAccess.requireOwn(me);
        OrderResponse r = responseRepository.findByOrderIdAndProviderId(orderId, provider.getId())
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.RESPONSE_NOT_FOUND));
        return ResponseView.of(r, distance(r.getOrder(), r));
    }

    @Transactional(readOnly = true)
    public ResponseView get(AuthUser me, Long responseId) {
        OrderResponse r = load(responseId);
        Order order = r.getOrder();
        boolean author = r.getProvider().isOwnedBy(me.id());
        if (!author && !order.isOwnedBy(me.id()) && !me.isSupport()) throw BusinessException.forbidden();
        return ResponseView.of(r, distance(order, r));
    }

    @Transactional(readOnly = true)
    public PageResponse<ResponseView> mine(AuthUser me, Integer page, Integer size) {
        var pageable = PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return providerAccess.findOwn(me)
                .map(p -> PageResponse.of(responseRepository.findByProviderId(p.getId(), pageable), (OrderResponse x) -> ResponseView.of(x, distance(x.getOrder(), x))))
                .orElseGet(() -> PageResponse.of(responseRepository.findForCustomer(me.id(), pageable), (OrderResponse x) -> ResponseView.of(x, distance(x.getOrder(), x))));
    }

    // ------------------------------------------------------------------ expiration (job)

    /** Expires one pending offer whose own expiry passed. Takes the order lock first, like accept does. */
    @Transactional
    public void expire(Long responseId) {
        OrderResponse probe = load(responseId);
        Order order = orderAccess.loadForUpdate(probe.getOrder().getId());
        OrderResponse r = load(responseId);
        Instant now = clock.instant();
        if (r.getStatus() != ResponseStatus.PENDING || !r.isExpiredAt(now)) return;
        r.changeStatus(ResponseStatus.EXPIRED, now);
        fallBackToQuoting(order);
        timeline.system(order.getId(), TimelineEvent.OFFER_EXPIRED, "offer " + r.getId());
        audit.system(AuditAction.RESPONSE_EXPIRED, "RESPONSE", r.getId(), null);
        notifications.notify(order.getCustomer().getId(), NotificationType.RESPONSE_EXPIRED, "Offer expired",
                "The offer from " + r.getProvider().getName() + " on order #" + order.getId() + " expired.", order.getId());
        notifications.notify(r.getProvider().getUser().getId(), NotificationType.RESPONSE_EXPIRED, "Offer expired",
                "Your offer on order #" + order.getId() + " expired.", order.getId());
    }

    // ------------------------------------------------------------------ helpers

    private void fallBackToQuoting(Order order) {
        if (order.getStatus() == OrderStatus.RESPONSES_AVAILABLE
                && responseRepository.countByOrderIdAndStatus(order.getId(), ResponseStatus.PENDING) == 0) {
            OrderStateMachine.transition(order, OrderStatus.QUOTING, clock.instant());
        }
    }

    private void applyPricing(OrderResponse resp, Order order, BigDecimal labor, BigDecimal parts, BigDecimal discount,
                              List<PartPriceRequest> partPrices) {
        BigDecimal laborCost = labor == null ? BigDecimal.ZERO : labor;
        BigDecimal partsCost = parts == null ? BigDecimal.ZERO : parts;
        if (partPrices != null) {
            Map<Long, OrderSparePart> orderParts = order.getSpareParts().stream().collect(Collectors.toMap(OrderSparePart::getId, Function.identity()));
            Set<Long> seen = new HashSet<>();
            resp.getPartPrices().clear();
            responseRepository.flush();
            partsCost = BigDecimal.ZERO;
            for (PartPriceRequest pp : partPrices) {
                OrderSparePart part = orderParts.get(pp.sparePartId());
                if (part == null) throw BusinessException.validation("Spare part " + pp.sparePartId() + " does not belong to this order");
                if (!seen.add(pp.sparePartId())) throw BusinessException.validation("Spare part " + pp.sparePartId() + " is priced twice");
                ResponsePartPrice price = new ResponsePartPrice();
                price.setResponse(resp);
                price.setSparePart(part);
                price.setPrice(pp.price());
                resp.getPartPrices().add(price);
                partsCost = partsCost.add(pp.price());
            }
        }
        BigDecimal disc = discount == null ? BigDecimal.ZERO : discount;
        BigDecimal gross = laborCost.add(partsCost);
        if (disc.compareTo(gross) > 0) throw BusinessException.validation("discount cannot exceed labor + parts");
        BigDecimal price = gross.subtract(disc);
        if (price.signum() <= 0) throw BusinessException.validation("The offer price must be greater than zero");
        resp.setLaborCost(laborCost);
        resp.setPartsCost(partsCost);
        resp.setDiscount(disc);
        resp.setPrice(price);
    }

    /** Validates the requested validity window, then caps the expiry at the order deadline. */
    private Instant resolveExpiry(Order order, Instant requested, Instant now) {
        WadiiProperties.OfferRules rules = properties.getOffer();
        Instant expiresAt = requested != null ? requested : now.plus(Duration.ofHours(rules.getDefaultValidityHours()));
        if (!expiresAt.isAfter(now)
                || expiresAt.isBefore(now.plus(Duration.ofHours(rules.getMinValidityHours())))
                || expiresAt.isAfter(now.plus(Duration.ofHours(rules.getMaxValidityHours())))) {
            throw BusinessException.of(ErrorCode.INVALID_EXPIRY, "expiresAt must be between " + rules.getMinValidityHours()
                    + " hour(s) and " + rules.getMaxValidityHours() + " hours from now");
        }
        return expiresAt.isAfter(order.getResponseDeadline()) ? order.getResponseDeadline() : expiresAt;
    }

    private ProviderBranch resolveBranch(Provider provider, Long branchId, OrderProviderTarget target) {
        Long id = branchId != null ? branchId : target == null ? null : target.getBranchId();
        if (id == null) return null;
        ProviderBranch b = branchRepository.findById(id).orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND));
        if (!b.getProvider().getId().equals(provider.getId())) throw BusinessException.forbidden("This branch belongs to another provider");
        if (!b.isActive()) throw BusinessException.validation("The branch is not active");
        return b;
    }

    private void applyProposedTime(OrderResponse resp, Instant proposed) {
        if (proposed == null) return;
        int duration = resp.getEstimatedDurationMinutes() == null ? 60 : resp.getEstimatedDurationMinutes();
        appointmentService.validateSlot(resp.getBranch(), proposed, Math.min(duration, 12 * 60));
        resp.setProposedAppointmentAt(proposed);
    }

    private BigDecimal distance(Order order, OrderResponse r) {
        if (r.getBranch() == null || !order.hasLocation() || !r.getBranch().hasLocation()) return null;
        return Geo.distanceKm(order.getLatitude(), order.getLongitude(), r.getBranch().getLatitude(), r.getBranch().getLongitude());
    }

    private Comparator<OrderResponse> comparator(SortMode sort, Order order, Map<Long, BigDecimal> distances, List<OrderResponse> all) {
        Comparator<OrderResponse> byId = Comparator.comparing(OrderResponse::getId);
        Comparator<OrderResponse> live = Comparator.comparing(x -> x.getStatus() == ResponseStatus.PENDING ? 0 : 1);
        return switch (sort) {
            case LOWEST_PRICE -> live.thenComparing(OrderResponse::getPrice).thenComparing(byId);
            case HIGHEST_RATED -> live.thenComparing((OrderResponse x) -> rankingService.bayesianRating(x.getProvider()), Comparator.reverseOrder()).thenComparing(byId);
            case NEAREST -> live.thenComparing((OrderResponse x) -> distances.get(x.getId()), Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(byId);
            case FASTEST_RESPONSE -> live.thenComparing((OrderResponse x) -> Duration.between(order.getCreatedAt(), x.getCreatedAt())).thenComparing(byId);
            case RECOMMENDED -> {
                Map<Long, ProviderStats> stats = statsRepository.findByProviderIdIn(all.stream().map(x -> x.getProvider().getId()).toList())
                        .stream().collect(Collectors.toMap(ProviderStats::getProviderId, Function.identity()));
                BigDecimal minPrice = all.stream().map(OrderResponse::getPrice).min(Comparator.naturalOrder()).orElse(BigDecimal.ONE);
                Map<Long, Double> score = new HashMap<>();
                for (OrderResponse x : all) {
                    BigDecimal radius = properties.getOrder().getDefaultServiceRadiusKm();
                    double providerScore = rankingService.score(x.getProvider(), stats.get(x.getProvider().getId()), distances.get(x.getId()), radius).doubleValue();
                    double priceScore = 100 * minPrice.doubleValue() / Math.max(x.getPrice().doubleValue(), 0.01);
                    score.put(x.getId(), 0.6 * providerScore + 0.4 * priceScore);
                }
                yield live.thenComparing((OrderResponse x) -> score.get(x.getId()), Comparator.reverseOrder()).thenComparing(byId);
            }
        };
    }

    private OrderResponse load(Long id) {
        return responseRepository.findById(id).orElseThrow(() -> BusinessException.notFound(ErrorCode.RESPONSE_NOT_FOUND));
    }
}
