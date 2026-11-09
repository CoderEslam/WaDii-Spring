package com.doubleclick.wadii.offer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface OfferLineRepository extends JpaRepository<OfferLine, Long> {

    List<OfferLine> findByOfferIdOrderByIdAsc(Long offerId);

    List<OfferLine> findByOfferIdIn(Collection<Long> offerIds);

    List<OfferLine> findByServiceItemIdIn(Collection<Long> itemIds);

    List<OfferLine> findByServiceItemIdAndStatus(Long itemId, OfferLine.Status status);

    @Query("""
            select count(l) from OfferLine l, Offer o
            where l.offerId = o.id and o.requestId = :requestId
            """)
    long countForRequest(@Param("requestId") Long requestId);

    @Query("""
            select count(l) from OfferLine l, Offer o
            where l.offerId = o.id and o.providerId = :providerId and l.status = com.doubleclick.wadii.offer.OfferLine.Status.ACCEPTED
              and l.updatedAt >= :since
            """)
    long countAccepted(@Param("providerId") Long providerId, @Param("since") Instant since);

    @Modifying(flushAutomatically = true)
    @Query("delete from OfferLine l where l.offerId = :offerId and l.status in :statuses")
    void deleteByOfferIdAndStatusIn(@Param("offerId") Long offerId, @Param("statuses") Collection<OfferLine.Status> statuses);
}
