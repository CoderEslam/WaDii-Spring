package com.doubleclick.wadii.provider;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface BranchHoursRepository extends JpaRepository<BranchHours, Long> {

    List<BranchHours> findByBranchIdIn(Collection<Long> branchIds);

    List<BranchHours> findByBranchId(Long branchId);

    /** Bulk delete runs immediately, so replacement rows for the same weekdays can be inserted afterwards. */
    @Modifying(flushAutomatically = true)
    @Query("delete from BranchHours h where h.branchId = :branchId")
    void deleteByBranchId(@Param("branchId") Long branchId);
}
