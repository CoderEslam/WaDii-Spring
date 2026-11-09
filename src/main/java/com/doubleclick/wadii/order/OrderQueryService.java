package com.doubleclick.wadii.order;

import com.doubleclick.wadii.catalog.CatalogService;
import com.doubleclick.wadii.catalog.CatalogService.CategoryView;
import com.doubleclick.wadii.common.api.PageResponse;
import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.common.util.KeyList;
import com.doubleclick.wadii.dispute.Dispute;
import com.doubleclick.wadii.dispute.DisputeRepository;
import com.doubleclick.wadii.identity.User;
import com.doubleclick.wadii.identity.UserRepository;
import com.doubleclick.wadii.offer.OfferLinePartRepository;
import com.doubleclick.wadii.offer.OfferViews.PartView;
import com.doubleclick.wadii.offer.OfferViews.Pricing;
import com.doubleclick.wadii.offer.OfferViews.Warranty;
import com.doubleclick.wadii.provider.Provider;
import com.doubleclick.wadii.provider.ProviderBranch;
import com.doubleclick.wadii.provider.ProviderBranchRepository;
import com.doubleclick.wadii.provider.ProviderRepository;
import com.doubleclick.wadii.request.RequestQueryService.VehicleView;
import com.doubleclick.wadii.request.ServiceItemRepository;
import com.doubleclick.wadii.request.ServiceRequestRepository;
import com.doubleclick.wadii.warranty.WarrantyRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Order read models. {@code actions} tells the client exactly what the caller may do now. */
@Service
@RequiredArgsConstructor
public class OrderQueryService {

    public enum Action {
        START, MARK_COMPLETED, CONFIRM_COMPLETION, COMPLAIN, CANCEL, RESCHEDULE, REPORT_NO_SHOW, REQUEST_INTERRUPTION,
        SUBMIT_INTERRUPTION_STATEMENT, ACCEPT_INTERRUPTION, DISPUTE_INTERRUPTION, REPORT_CASH, CONFIRM_CASH,
        DISPUTE_CASH, PROPOSE_CHARGE, RATE
    }

    public enum Role { CUSTOMER, PROVIDER, ADMIN }

    private final ServiceOrderRepository orders;
    private final OrderService orderService;
    private final OrderChargeRepository charges;
    private final OrderEventRepository events;
    private final RatingRepository ratings;
    private final OfferLinePartRepository parts;
    private final ServiceRequestRepository requests;
    private final ServiceItemRepository items;
    private final ProviderRepository providers;
    private final ProviderBranchRepository branches;
    private final UserRepository users;
    private final WarrantyRepository warranties;
    private final DisputeRepository disputes;
    private final CatalogService catalog;
    private final ObjectMapper objectMapper;

    public record CustomerInfo(Long id, String fullName, String phone) {
    }

    public record ProviderInfo(Long id, String businessName, String phone) {
    }

    public record BranchInfo(Long id, String name, String address, String phone, BigDecimal latitude, BigDecimal longitude) {
    }

    public record Cancellation(ServiceOrder.CancellationKind kind, String reason, Long byUserId) {
    }

    public record Interruption(Instant requestedAt, String reason, String workDone, BigDecimal laborCost, BigDecimal partsCost,
                               BigDecimal proposedAmount, List<String> evidenceKeys, Instant statementAt,
                               ServiceOrder.InterruptionOutcome outcome, BigDecimal finalAmount) {
    }

    public record ChargeView(Long id, String description, BigDecimal amount, OrderCharge.Status status, Instant createdAt,
                             Instant decidedAt) {
        public static ChargeView of(OrderCharge c) {
            return new ChargeView(c.getId(), c.getDescription(), c.getAmount(), c.getStatus(), c.getCreatedAt(), c.getDecidedAt());
        }
    }

    public record RatingView(Long id, Long orderId, int stars, String comment, String customerName, Instant createdAt, boolean hidden) {
    }

    public record EventView(Long id, String type, OrderEvent.ActorRole actorRole, String note, Instant createdAt) {
    }

    public record OrderSummary(Long id, Long requestId, CategoryView category, ServiceOrder.Status status,
                               ServiceOrder.CashStatus cashStatus, String counterpartName, BigDecimal customerTotal,
                               Instant appointmentStart, Instant createdAt) {
    }

    public record OrderDetail(Long id, Long requestId, Long serviceItemId, CategoryView category, String itemDescription,
                              ServiceOrder.Status status, ServiceOrder.CashStatus cashStatus, Role myRole,
                              CustomerInfo customer, ProviderInfo provider, BranchInfo branch, VehicleView vehicle, Pricing pricing,
                              List<PartView> parts, BigDecimal approvedChargesTotal, BigDecimal amountDue,
                              BigDecimal cashReportedAmount, Instant cashReportedAt, Instant cashConfirmedAt,
                              Warranty warranty, Long warrantyId, Instant warrantyEndsAt, Instant appointmentStart,
                              Instant appointmentEnd, Instant startedAt, Instant providerCompletedAt, Instant completedAt,
                              Instant canceledAt, Cancellation cancellation, Interruption interruption, List<ChargeView> charges,
                              JsonNode policySnapshot, String invoiceNumber, RatingView rating, boolean canRate,
                              Long openDisputeId, List<Action> actions, Instant createdAt) {
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummary> list(AuthUser user, boolean asProvider, ServiceOrder.Status status, Integer page, Integer size) {
        var pageable = PageResponse.pageable(page, size);
        var result = asProvider
                ? orders.forProvider(providers.findByUserId(user.id()).map(Provider::getId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.PROVIDER_NOT_FOUND)), status, pageable)
                : orders.forCustomer(user.id(), status, pageable);
        return summaries(result, asProvider);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummary> adminList(ServiceOrder.Status status, ServiceOrder.CashStatus cashStatus, Long providerId,
                                                Long categoryId, Integer page, Integer size) {
        return summaries(orders.adminSearch(status, cashStatus, providerId, categoryId, PageResponse.pageable(page, size)), true);
    }

    @Transactional(readOnly = true)
    public OrderDetail detail(AuthUser user, Long id) {
        ServiceOrder o = orderService.visible(user, id);
        Role role = o.getCustomerId().equals(user.id()) ? Role.CUSTOMER : o.getProviderUserId().equals(user.id()) ? Role.PROVIDER : Role.ADMIN;
        User customer = users.findById(o.getCustomerId()).orElseThrow();
        Provider provider = providers.findById(o.getProviderId()).orElseThrow();
        ProviderBranch branch = branches.findById(o.getBranchId()).orElseThrow();
        var request = requests.findById(o.getRequestId()).orElseThrow();
        var item = items.findById(o.getServiceItemId()).orElseThrow();
        Rating rating = ratings.findByOrderId(id).orElse(null);
        var warranty = warranties.findByOrderId(id).orElse(null);
        Long openDispute = disputes.findByOrderIdAndStatusIn(id, EnumSet.of(Dispute.Status.OPEN, Dispute.Status.UNDER_REVIEW))
                .stream().map(Dispute::getId).findFirst().orElse(null);
        List<OrderCharge> chargeList = charges.findByOrderIdOrderByIdAsc(id);
        Pricing pricing = new Pricing(o.getServicePrice(), o.getLaborCost(), o.getPartsTotal(), o.getDiscount(), o.getProviderTotal(),
                o.getCommissionAmount(), o.getCommissionPayer(), o.getCustomerTotal());
        Warranty w = o.isWarrantyProvided()
                ? new Warranty(true, o.getWarrantyDays(), o.getWarrantyCoverage(), o.getWarrantyExclusions(), o.getWarrantyConditions())
                : new Warranty(false, null, null, null, o.getWarrantyConditions());
        Interruption interruption = o.getInterruptionRequestedAt() == null ? null : new Interruption(o.getInterruptionRequestedAt(),
                o.getInterruptionReason(), o.getInterruptionWorkDone(), o.getInterruptionLabor(), o.getInterruptionParts(),
                o.getInterruptionProposed(), KeyList.split(o.getInterruptionEvidence()), o.getInterruptionStatementAt(),
                o.getInterruptionOutcome(), o.getFinalAmount());
        return new OrderDetail(o.getId(), o.getRequestId(), o.getServiceItemId(), category(o.getCategoryId()), item.getDescription(),
                o.getStatus(), o.getCashStatus(), role,
                new CustomerInfo(customer.getId(), customer.getFullName(), customer.getPhone()),
                new ProviderInfo(provider.getId(), provider.getBusinessName(), provider.getPhone()),
                new BranchInfo(branch.getId(), branch.getName(), branch.getAddress(), branch.getPhone(), branch.getLatitude(), branch.getLongitude()),
                VehicleView.of(request), pricing,
                parts.findByLineIdIn(List.of(o.getLineId())).stream().map(PartView::of).toList(),
                o.getApprovedChargesTotal(), o.amountDue(), o.getCashReportedAmount(), o.getCashReportedAt(), o.getCashConfirmedAt(),
                w, warranty == null ? null : warranty.getId(), warranty == null ? null : warranty.getEndsAt(),
                o.getAppointmentStart(), o.getAppointmentEnd(), o.getStartedAt(), o.getProviderCompletedAt(), o.getCompletedAt(),
                o.getCanceledAt(), o.getCancellationKind() == null ? null
                : new Cancellation(o.getCancellationKind(), o.getCancellationReason(), o.getCanceledBy()),
                interruption, chargeList.stream().map(ChargeView::of).toList(), parse(o.getPolicySnapshot()), o.getInvoiceNumber(),
                rating == null ? null : ratingView(rating, customer.getFullName()),
                role == Role.CUSTOMER && o.getStatus() == ServiceOrder.Status.COMPLETED && rating == null,
                openDispute, actions(o, role, rating != null), o.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<EventView> events(AuthUser user, Long id) {
        orderService.visible(user, id);
        return events.findByOrderIdOrderByIdAsc(id).stream()
                .map(e -> new EventView(e.getId(), e.getType(), e.getActorRole(), e.getNote(), e.getCreatedAt())).toList();
    }

    public static RatingView ratingView(Rating r, String customerName) {
        return new RatingView(r.getId(), r.getOrderId(), r.getStars(), r.getComment(), customerName, r.getCreatedAt(), r.isHidden());
    }

    List<Action> actions(ServiceOrder o, Role role, boolean rated) {
        List<Action> list = new ArrayList<>();
        ServiceOrder.Status s = o.getStatus();
        if (role == Role.CUSTOMER) {
            switch (s) {
                case ACCEPTED -> {
                    list.add(Action.CANCEL);
                    list.add(Action.RESCHEDULE);
                    if (orderService.noShowReportable(o)) list.add(Action.REPORT_NO_SHOW);
                }
                case IN_PROGRESS -> list.add(Action.REQUEST_INTERRUPTION);
                case PROVIDER_MARKED_COMPLETED -> {
                    list.add(Action.CONFIRM_COMPLETION);
                    list.add(Action.COMPLAIN);
                }
                case INTERRUPTION_STATEMENT_SUBMITTED -> {
                    list.add(Action.ACCEPT_INTERRUPTION);
                    list.add(Action.DISPUTE_INTERRUPTION);
                }
                case COMPLETED -> {
                    if (!rated) list.add(Action.RATE);
                }
                default -> {
                }
            }
            if (OrderService.cashAwaitingCustomer(o)) {
                list.add(Action.CONFIRM_CASH);
                list.add(Action.DISPUTE_CASH);
            }
        } else if (role == Role.PROVIDER) {
            switch (s) {
                case ACCEPTED -> {
                    list.add(Action.START);
                    list.add(Action.CANCEL);
                    list.add(Action.RESCHEDULE);
                    if (orderService.noShowReportable(o)) list.add(Action.REPORT_NO_SHOW);
                }
                case IN_PROGRESS -> {
                    list.add(Action.MARK_COMPLETED);
                    list.add(Action.PROPOSE_CHARGE);
                }
                case INTERRUPTION_REQUESTED -> list.add(Action.SUBMIT_INTERRUPTION_STATEMENT);
                default -> {
                }
            }
            if (OrderService.CASH_REPORTABLE.contains(s) && OrderService.CASH_OPEN.contains(o.getCashStatus())) {
                list.add(Action.REPORT_CASH);
            }
        }
        return list;
    }

    private PageResponse<OrderSummary> summaries(org.springframework.data.domain.Page<ServiceOrder> page, boolean counterpartIsCustomer) {
        List<ServiceOrder> content = page.getContent();
        Map<Long, String> customerNames = users.findAllById(content.stream().map(ServiceOrder::getCustomerId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getFullName));
        Map<Long, String> providerNames = providers.findAllById(content.stream().map(ServiceOrder::getProviderId).distinct().toList())
                .stream().collect(Collectors.toMap(Provider::getId, Provider::getBusinessName));
        Map<Long, CategoryView> categories = catalog.allCategories().stream().collect(Collectors.toMap(CategoryView::id, Function.identity()));
        return PageResponse.of(page, o -> new OrderSummary(o.getId(), o.getRequestId(), categories.get(o.getCategoryId()),
                o.getStatus(), o.getCashStatus(),
                counterpartIsCustomer ? customerNames.get(o.getCustomerId()) : providerNames.get(o.getProviderId()),
                o.getCustomerTotal(), o.getAppointmentStart(), o.getCreatedAt()));
    }

    private CategoryView category(Long id) {
        return CategoryView.of(catalog.category(id));
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid policy snapshot", e);
        }
    }
}
