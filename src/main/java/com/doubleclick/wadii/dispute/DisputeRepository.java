package com.doubleclick.wadii.dispute;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DisputeRepository extends JpaRepository<Dispute, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Dispute d where d.id = :id")
    Optional<Dispute> lockById(@Param("id") Long id);

    @Query("select d from Dispute d where d.openedBy = :userId or d.respondentUserId = :userId order by d.id desc")
    Page<Dispute> involving(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            select d from Dispute d where (:status is null or d.status = :status) and (:type is null or d.type = :type)
            order by d.id desc
            """)
    Page<Dispute> search(@Param("status") Dispute.Status status, @Param("type") Dispute.Type type, Pageable pageable);

    List<Dispute> findByOrderIdAndStatusIn(Long orderId, Collection<Dispute.Status> statuses);

    boolean existsByOrderIdAndTypeAndStatusIn(Long orderId, Dispute.Type type, Collection<Dispute.Status> statuses);

    boolean existsByListingIdAndOpenedByAndStatusIn(Long listingId, Long openedBy, Collection<Dispute.Status> statuses);

    long countByOpenedByAndCreatedAtAfter(Long openedBy, Instant since);

    long countByStatusIn(Collection<Dispute.Status> statuses);

    @Query("select count(d) from Dispute d where d.orderId in (select o.id from com.doubleclick.wadii.order.ServiceOrder o where o.providerId = :providerId) and d.createdAt >= :since")
    long countForProvider(@Param("providerId") Long providerId, @Param("since") Instant since);
}
