package com.doubleclick.wadii.repository;

import com.doubleclick.wadii.entities.WorkTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WorkTimeRepository extends JpaRepository<WorkTime, Long> {

    @Query("SELECT w FROM WorkTime w WHERE w.provider.id = :providerId ORDER BY w.day")
    List<WorkTime> findAllByProviderId(@Param("providerId") Long providerId);

}
