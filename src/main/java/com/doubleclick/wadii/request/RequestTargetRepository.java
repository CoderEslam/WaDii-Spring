package com.doubleclick.wadii.request;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RequestTargetRepository extends JpaRepository<RequestTarget, Long> {

    Optional<RequestTarget> findByRequestIdAndProviderId(Long requestId, Long providerId);

    List<RequestTarget> findByRequestId(Long requestId);

    List<RequestTarget> findByProviderIdAndStatusIn(Long providerId, java.util.Collection<RequestTarget.Status> statuses);

    long countByRequestId(Long requestId);

    @Query("""
            select t from RequestTarget t where t.providerId = :providerId and (:status is null or t.status = :status)
            order by t.id desc
            """)
    Page<RequestTarget> inbox(@Param("providerId") Long providerId, @Param("status") RequestTarget.Status status, Pageable pageable);

    @Modifying(flushAutomatically = true)
    @Query("delete from RequestTarget t where t.requestId = :requestId")
    void deleteByRequestId(@Param("requestId") Long requestId);

    @Query("select count(t) from RequestTarget t where t.providerId = :providerId and t.createdAt >= :since")
    long countInvited(@Param("providerId") Long providerId, @Param("since") Instant since);

    @Query("select count(t) from RequestTarget t where t.providerId = :providerId and t.createdAt >= :since and t.viewedAt is not null")
    long countViewed(@Param("providerId") Long providerId, @Param("since") Instant since);

    @Query("select t from RequestTarget t where t.providerId = :providerId and t.createdAt >= :since and t.respondedAt is not null")
    List<RequestTarget> responded(@Param("providerId") Long providerId, @Param("since") Instant since);
}
