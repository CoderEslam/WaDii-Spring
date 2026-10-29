package com.doubleclick.wadii.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderProviderTargetRepository extends JpaRepository<OrderProviderTarget, Long> {

    Optional<OrderProviderTarget> findByOrderIdAndProviderId(Long orderId, Long providerId);

    boolean existsByOrderIdAndProviderId(Long orderId, Long providerId);

    List<OrderProviderTarget> findByOrderId(Long orderId);

    long countByOrderId(Long orderId);

    @Query("SELECT t FROM OrderProviderTarget t JOIN FETCH t.order o WHERE t.provider.id = :providerId")
    List<OrderProviderTarget> findAllForProvider(@Param("providerId") Long providerId);

    @Query(value = "SELECT t FROM OrderProviderTarget t JOIN t.order o WHERE t.provider.id = :providerId AND o.status IN :statuses",
            countQuery = "SELECT COUNT(t) FROM OrderProviderTarget t JOIN t.order o WHERE t.provider.id = :providerId AND o.status IN :statuses")
    Page<OrderProviderTarget> findForProvider(@Param("providerId") Long providerId,
                                              @Param("statuses") Collection<OrderStatus> statuses,
                                              Pageable pageable);

    @Modifying
    @Query("UPDATE OrderProviderTarget t SET t.status = com.doubleclick.wadii.order.TargetStatus.EXPIRED, t.updatedAt = :now "
            + "WHERE t.order.id = :orderId AND t.status IN (com.doubleclick.wadii.order.TargetStatus.TARGETED, com.doubleclick.wadii.order.TargetStatus.VIEWED)")
    int expireOpenTargets(@Param("orderId") Long orderId, @Param("now") Instant now);
}
