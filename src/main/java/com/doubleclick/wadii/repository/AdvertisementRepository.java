package com.doubleclick.wadii.repository;

import com.doubleclick.wadii.entities.Advertisement;
import com.doubleclick.wadii.entities.AdvertisementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AdvertisementRepository extends JpaRepository<Advertisement, Long> {

    List<Advertisement> findByStatus(AdvertisementStatus status);

    List<Advertisement> findByAdvertiserName(String advertiserName);

    List<Advertisement> findAllByOrderByPriorityDesc();

    @Query("SELECT a FROM Advertisement a WHERE a.status = 'ACTIVE' AND a.startDate <= :now AND a.endDate >= :now ORDER BY a.priority DESC")
    List<Advertisement> findActiveAdvertisements(LocalDateTime now);
}
