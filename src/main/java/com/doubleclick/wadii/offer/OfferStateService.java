package com.doubleclick.wadii.offer;

import com.doubleclick.wadii.notification.NotificationService;
import com.doubleclick.wadii.notification.NotificationType;
import com.doubleclick.wadii.provider.ProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * Line-level transitions shared by requests, offers and acceptance. An ACTIVE offer becomes CLOSED once none of
 * its lines is pending any more; WITHDRAWN and EXPIRED offers keep their status.
 */
@Service
@RequiredArgsConstructor
public class OfferStateService {

    private final OfferRepository offers;
    private final OfferLineRepository lines;
    private final ProviderRepository providers;
    private final NotificationService notifications;

    /** Moves every PENDING line of the given items to {@code target} and tells each affected provider. */
    @Transactional
    public void closePendingLines(Collection<Long> itemIds, OfferLine.Status target, String reasonForProvider) {
        if (itemIds.isEmpty()) {
            return;
        }
        List<OfferLine> pending = lines.findByServiceItemIdIn(itemIds).stream()
                .filter(l -> l.getStatus() == OfferLine.Status.PENDING).toList();
        pending.forEach(l -> l.setStatus(target));
        pending.stream().map(OfferLine::getOfferId).distinct().forEach(offerId -> {
            Offer offer = refresh(offerId);
            NotificationType type = target == OfferLine.Status.EXPIRED ? NotificationType.OFFER_EXPIRED : NotificationType.OFFER_DECLINED;
            providers.findById(offer.getProviderId()).ifPresent(p -> notifications.notify(p.getUserId(), type,
                    target == OfferLine.Status.EXPIRED ? "Offer expired" : "Offer not selected",
                    reasonForProvider, "OFFER", offerId));
        });
    }

    @Transactional
    public Offer refresh(Long offerId) {
        Offer offer = offers.findById(offerId).orElseThrow();
        if (offer.getStatus() == Offer.Status.ACTIVE
                && lines.findByOfferIdOrderByIdAsc(offerId).stream().noneMatch(l -> l.getStatus() == OfferLine.Status.PENDING)) {
            offer.setStatus(Offer.Status.CLOSED);
        }
        return offer;
    }
}
