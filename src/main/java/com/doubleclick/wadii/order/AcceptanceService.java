package com.doubleclick.wadii.order;

import com.doubleclick.wadii.common.error.BusinessException;
import com.doubleclick.wadii.common.error.ErrorCode;
import com.doubleclick.wadii.common.security.AuthUser;
import com.doubleclick.wadii.notification.NotificationService;
import com.doubleclick.wadii.notification.NotificationType;
import com.doubleclick.wadii.offer.Offer;
import com.doubleclick.wadii.offer.OfferLine;
import com.doubleclick.wadii.offer.OfferLineRepository;
import com.doubleclick.wadii.offer.OfferRepository;
import com.doubleclick.wadii.offer.OfferStateService;
import com.doubleclick.wadii.offer.OfferViews.Warranty;
import com.doubleclick.wadii.provider.Provider;
import com.doubleclick.wadii.provider.ProviderDefaults;
import com.doubleclick.wadii.provider.ProviderPolicyRepository;
import com.doubleclick.wadii.provider.ProviderRepository;
import com.doubleclick.wadii.request.RequestStatusService;
import com.doubleclick.wadii.request.ServiceItem;
import com.doubleclick.wadii.request.ServiceItemRepository;
import com.doubleclick.wadii.request.ServiceRequest;
import com.doubleclick.wadii.request.ServiceRequestRepository;
import com.doubleclick.wadii.settings.PlatformConfigService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Accepting one offer line for one service item. Runs in a single transaction under a lock on the item:
 * the line becomes ACCEPTED, competing pending lines for the same item (and only that item) are declined, and a
 * Service Order is created with the price, commission, warranty and policy snapshots.
 */
@Service
@RequiredArgsConstructor
public class AcceptanceService {

    private final ServiceRequestRepository requests;
    private final ServiceItemRepository items;
    private final OfferRepository offers;
    private final OfferLineRepository lines;
    private final OfferStateService offerState;
    private final RequestStatusService requestStatus;
    private final ProviderRepository providers;
    private final ProviderPolicyRepository policies;
    private final ServiceOrderRepository orders;
    private final AppointmentPolicy appointments;
    private final OrderTimeline timeline;
    private final PlatformConfigService config;
    private final NotificationService notifications;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public ServiceOrder accept(AuthUser customer, Long requestId, Long itemId, Long lineId, Instant appointmentStart) {
        ServiceRequest request = requests.findById(requestId).filter(r -> r.getCustomerId().equals(customer.id()))
                .orElseThrow(() -> BusinessException.of(ErrorCode.REQUEST_NOT_FOUND));
        ServiceItem item = items.lockById(itemId).filter(i -> i.getRequestId().equals(requestId))
                .orElseThrow(() -> BusinessException.of(ErrorCode.NOT_FOUND));
        if (!item.isOpen()) {
            throw BusinessException.of(ErrorCode.ITEM_NOT_OPEN);
        }
        Instant now = Instant.now(clock);
        if (!request.getResponseDeadline().isAfter(now)) {
            throw BusinessException.of(ErrorCode.REQUEST_DEADLINE_PASSED);
        }
        OfferLine line = lines.findById(lineId).filter(l -> l.getServiceItemId().equals(itemId))
                .orElseThrow(() -> BusinessException.of(ErrorCode.OFFER_NOT_FOUND));
        Offer offer = offers.findById(line.getOfferId()).orElseThrow();
        if (line.getStatus() != OfferLine.Status.PENDING || offer.getStatus() != Offer.Status.ACTIVE) {
            throw BusinessException.of(ErrorCode.OFFER_NOT_PENDING);
        }
        if (!offer.getExpiresAt().isAfter(now)) {
            throw BusinessException.of(ErrorCode.OFFER_EXPIRED);
        }
        Provider provider = providers.findById(offer.getProviderId()).orElseThrow();
        if (provider.getUserId().equals(customer.id())) {
            throw BusinessException.of(ErrorCode.SELF_DEALING_FORBIDDEN);
        }
        if (!provider.canOperate()) {
            throw BusinessException.of(ErrorCode.PROVIDER_NOT_ELIGIBLE);
        }
        Instant start = appointmentStart != null ? appointmentStart : line.getProposedStart();
        AppointmentPolicy.Slot slot = start == null ? null : appointments.reserve(offer.getBranchId(), start, line.getEstimatedMinutes(), null);

        line.setStatus(OfferLine.Status.ACCEPTED);
        ServiceOrder order = newOrder(request, item, offer, line, provider, slot);
        orders.save(order);
        line.setOrderId(order.getId());
        item.setStatus(ServiceItem.Status.ACCEPTED);
        item.setOrderId(order.getId());
        offerState.closePendingLines(List.of(itemId), OfferLine.Status.DECLINED,
                "The customer chose another offer for this service.");
        offerState.refresh(offer.getId());
        requestStatus.refresh(requestId);

        timeline.record(order, "ACCEPTED", customer.id(), OrderEvent.ActorRole.CUSTOMER,
                slot == null ? "Appointment to be agreed" : "Appointment " + slot.start());
        notifications.notify(provider.getUserId(), NotificationType.OFFER_ACCEPTED, "Offer accepted",
                "Your offer was accepted. Order #" + order.getId() + (slot == null ? "." : " is booked for " + slot.start() + "."),
                "ORDER", order.getId());
        return order;
    }

    private ServiceOrder newOrder(ServiceRequest request, ServiceItem item, Offer offer, OfferLine line, Provider provider,
                                  AppointmentPolicy.Slot slot) {
        ServiceOrder o = new ServiceOrder();
        o.setRequestId(request.getId());
        o.setServiceItemId(item.getId());
        o.setLineId(line.getId());
        o.setOfferId(offer.getId());
        o.setCustomerId(request.getCustomerId());
        o.setProviderId(provider.getId());
        o.setProviderUserId(provider.getUserId());
        o.setBranchId(offer.getBranchId());
        o.setCategoryId(item.getCategoryId());
        o.setServicePrice(line.getServicePrice());
        o.setLaborCost(line.getLaborCost());
        o.setPartsTotal(line.getPartsTotal());
        o.setDiscount(line.getDiscount());
        o.setProviderTotal(line.getProviderTotal());
        o.setCommissionMode(line.getCommissionMode());
        o.setCommissionValue(line.getCommissionValue());
        o.setCommissionBase(line.getProviderTotal());
        o.setCommissionAmount(line.getCommissionAmount());
        o.setCommissionPayer(line.getCommissionPayer());
        o.setCommissionConfigId(line.getCommissionConfigId());
        o.setCustomerTotal(line.getCustomerTotal());
        o.setWarrantyProvided(line.isWarrantyProvided());
        o.setWarrantyDays(line.getWarrantyDays());
        o.setWarrantyCoverage(line.getWarrantyCoverage());
        o.setWarrantyExclusions(line.getWarrantyExclusions());
        o.setWarrantyConditions(line.getWarrantyConditions());
        if (slot != null) {
            o.setAppointmentStart(slot.start());
            o.setAppointmentEnd(slot.end());
        }
        o.setPolicySnapshot(snapshot(provider, line));
        return o;
    }

    private String snapshot(Provider provider, OfferLine line) {
        var policy = policies.findFirstByProviderIdOrderByPolicyVersionDesc(provider.getId());
        var defaults = ProviderDefaults.POLICY;
        PolicySnapshot snapshot = new PolicySnapshot(
                policy.map(p -> p.getPolicyVersion()).orElse(null),
                policy.map(p -> p.getCancellationTerms()).orElse(defaults.cancellationTerms()),
                policy.map(p -> p.getNoShowTerms()).orElse(defaults.noShowTerms()),
                policy.map(p -> p.getServiceConditions()).orElse(defaults.serviceConditions()),
                policy.map(p -> p.getReturnTerms()).orElse(defaults.returnTerms()),
                PolicySnapshot.platformRules(config.marketplace().noShowGraceMinutes()),
                Warranty.of(line),
                new PolicySnapshot.Commission(line.getCommissionMode(), line.getCommissionValue(), line.getCommissionPayer(),
                        line.getProviderTotal(), line.getCommissionAmount()));
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
