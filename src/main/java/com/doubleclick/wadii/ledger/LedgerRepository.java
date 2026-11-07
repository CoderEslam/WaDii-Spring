package com.doubleclick.wadii.ledger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface LedgerRepository extends JpaRepository<LedgerEntry, Long> {

    @Query("""
            select e from LedgerEntry e
            where (:payer is null or e.payerUserId = :payer)
              and (:providerId is null or e.providerId = :providerId)
              and (:type is null or e.type = :type)
              and (:status is null or e.status = :status)
            order by e.id desc
            """)
    Page<LedgerEntry> search(@Param("payer") Long payer, @Param("providerId") Long providerId,
                             @Param("type") LedgerEntry.Type type, @Param("status") LedgerEntry.Status status,
                             Pageable pageable);

    List<LedgerEntry> findByPayerUserIdAndTypeOrderByIdDesc(Long payerUserId, LedgerEntry.Type type);

    @Query("""
            select coalesce(sum(e.amount), 0) from LedgerEntry e
            where e.payerUserId = :payer and e.type in :types and e.status in :statuses
            """)
    BigDecimal sumForPayer(@Param("payer") Long payer, @Param("types") Collection<LedgerEntry.Type> types,
                           @Param("statuses") Collection<LedgerEntry.Status> statuses);

    @Query("""
            select coalesce(sum(e.amount), 0) from LedgerEntry e
            where e.providerId = :providerId and e.type = :type and e.status in :statuses and e.createdAt >= :since
            """)
    BigDecimal sumForProvider(@Param("providerId") Long providerId, @Param("type") LedgerEntry.Type type,
                              @Param("statuses") Collection<LedgerEntry.Status> statuses, @Param("since") Instant since);

    @Query("""
            select coalesce(sum(e.amount), 0) from LedgerEntry e
            where e.type = :type and e.status in :statuses and e.createdAt >= :from and e.createdAt < :to
            """)
    BigDecimal sumInRange(@Param("type") LedgerEntry.Type type, @Param("statuses") Collection<LedgerEntry.Status> statuses,
                          @Param("from") Instant from, @Param("to") Instant to);

    boolean existsByProviderIdAndTypeAndDueDate(Long providerId, LedgerEntry.Type type, java.time.LocalDate dueDate);
}
