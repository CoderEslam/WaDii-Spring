package com.doubleclick.wadii.request;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ServiceItemRepository extends JpaRepository<ServiceItem, Long> {

    List<ServiceItem> findByRequestIdOrderByIdAsc(Long requestId);

    List<ServiceItem> findByRequestIdIn(Collection<Long> requestIds);

    /** Serializes acceptance of one service item so only one offer line can win it. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ServiceItem i where i.id = :id")
    Optional<ServiceItem> lockById(@Param("id") Long id);

    @Modifying(flushAutomatically = true)
    @Query("delete from ServiceItem i where i.requestId = :requestId")
    void deleteByRequestId(@Param("requestId") Long requestId);
}
