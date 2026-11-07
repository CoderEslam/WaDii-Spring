package com.doubleclick.wadii.request;

import com.doubleclick.wadii.notification.NotificationService;
import com.doubleclick.wadii.notification.NotificationType;
import com.doubleclick.wadii.offer.Offer;
import com.doubleclick.wadii.offer.OfferLine;
import com.doubleclick.wadii.offer.OfferLineRepository;
import com.doubleclick.wadii.offer.OfferRepository;
import com.doubleclick.wadii.offer.OfferStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

/**
 * Time-based transitions. When a request's response deadline passes, its unaccepted open items expire with their
 * pending lines; accepted items and their orders continue untouched. Offers past their own expiry stop being
 * acceptable even before the request deadline.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpirationJobs {

    private final ServiceRequestRepository requests;
    private final ServiceItemRepository items;
    private final OfferRepository offers;
    private final OfferLineRepository lines;
    private final OfferStateService offerState;
    private final RequestStatusService status;
    private final NotificationService notifications;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${wadii.jobs.expiration-interval-ms:60000}", initialDelay = 15_000)
    @Transactional
    public int expireOffers() {
        List<Long> ids = offers.expiredActive(Instant.now(clock));
        for (Offer offer : offers.findAllById(ids)) {
            lines.findByOfferIdOrderByIdAsc(offer.getId()).stream()
                    .filter(l -> l.getStatus() == OfferLine.Status.PENDING || l.getStatus() == OfferLine.Status.UNAVAILABLE)
                    .forEach(l -> l.setStatus(OfferLine.Status.EXPIRED));
            offer.setStatus(Offer.Status.EXPIRED);
            status.refresh(offer.getRequestId());
        }
        return ids.size();
    }

    @Scheduled(fixedDelayString = "${wadii.jobs.expiration-interval-ms:60000}", initialDelay = 16_000)
    @Transactional
    public int expireRequests() {
        List<Long> ids = requests.deadlinePassed(EnumSet.of(ServiceRequest.Status.MATCHING, ServiceRequest.Status.OFFERS_AVAILABLE,
                ServiceRequest.Status.PARTIALLY_MATCHED), Instant.now(clock));
        for (Long id : ids) {
            List<ServiceItem> open = items.findByRequestIdOrderByIdAsc(id).stream().filter(ServiceItem::isOpen).toList();
            open.forEach(i -> i.setStatus(ServiceItem.Status.EXPIRED));
            offerState.closePendingLines(open.stream().map(ServiceItem::getId).toList(), OfferLine.Status.EXPIRED,
                    "The customer's response deadline passed before a decision.");
            ServiceRequest r = status.refresh(id);
            if (!open.isEmpty()) {
                notifications.notify(r.getCustomerId(), NotificationType.REQUEST_EXPIRED, "Request deadline passed",
                        open.size() + " service(s) without an accepted offer expired. You can create a new request.", "REQUEST", id);
            }
        }
        if (!ids.isEmpty()) {
            log.info("Expired {} request(s)", ids.size());
        }
        return ids.size();
    }
}
