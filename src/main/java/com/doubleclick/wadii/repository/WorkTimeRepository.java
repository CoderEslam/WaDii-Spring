package com.doubleclick.wadii.repository;

import com.doubleclick.wadii.entities.WorkTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkTimeRepository extends JpaRepository<WorkTime, Long> {

    @Query("SELECT w FROM WorkTime w WHERE w.branch.id = :branchId ORDER BY w.day")
    List<WorkTime> findAllByBranchId(@Param("branchId") Long branchId);

    Optional<WorkTime> findByBranchIdAndDay(Long branchId, String day);

}
