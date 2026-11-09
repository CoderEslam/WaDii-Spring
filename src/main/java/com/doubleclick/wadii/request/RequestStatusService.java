package com.doubleclick.wadii.request;

import com.doubleclick.wadii.offer.OfferLine;
import com.doubleclick.wadii.offer.OfferLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Keeps open items and the parent request status in line with their children. An open item is OFFERS_AVAILABLE
 * while it has a pending line, else MATCHING. The parent only summarizes:
 * <ul>
 *   <li>some item accepted: PARTIALLY_MATCHED while other items are still open, else CLOSED;</li>
 *   <li>nothing accepted, items open: OFFERS_AVAILABLE if any has offers, else MATCHING;</li>
 *   <li>nothing accepted, nothing open: EXPIRED if any item expired, else CANCELED.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class RequestStatusService {

    private final ServiceRequestRepository requests;
    private final ServiceItemRepository items;
    private final OfferLineRepository lines;

    @Transactional
    public ServiceRequest refresh(Long requestId) {
        ServiceRequest request = requests.findById(requestId).orElseThrow();
        List<ServiceItem> list = items.findByRequestIdOrderByIdAsc(requestId);
        Set<Long> withPending = lines.findByServiceItemIdIn(list.stream().map(ServiceItem::getId).toList()).stream()
                .filter(l -> l.getStatus() == OfferLine.Status.PENDING)
                .map(OfferLine::getServiceItemId).collect(Collectors.toSet());
        for (ServiceItem item : list) {
            if (item.isOpen()) {
                item.setStatus(withPending.contains(item.getId()) ? ServiceItem.Status.OFFERS_AVAILABLE : ServiceItem.Status.MATCHING);
            }
        }
        boolean anyAccepted = list.stream().anyMatch(i -> i.getStatus() == ServiceItem.Status.ACCEPTED);
        boolean anyOpen = list.stream().anyMatch(ServiceItem::isOpen);
        boolean anyOffers = list.stream().anyMatch(i -> i.getStatus() == ServiceItem.Status.OFFERS_AVAILABLE);
        boolean anyExpired = list.stream().anyMatch(i -> i.getStatus() == ServiceItem.Status.EXPIRED);
        ServiceRequest.Status status;
        if (anyAccepted) {
            status = anyOpen ? ServiceRequest.Status.PARTIALLY_MATCHED : ServiceRequest.Status.CLOSED;
        } else if (anyOpen) {
            status = anyOffers ? ServiceRequest.Status.OFFERS_AVAILABLE : ServiceRequest.Status.MATCHING;
        } else {
            status = anyExpired ? ServiceRequest.Status.EXPIRED : ServiceRequest.Status.CANCELED;
        }
        request.setStatus(status);
        return request;
    }
}
