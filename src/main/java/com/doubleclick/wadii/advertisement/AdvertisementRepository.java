package com.doubleclick.wadii.advertisement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;

public interface AdvertisementRepository extends JpaRepository<Advertisement, Long> {

    Page<Advertisement> findByStatus(AdvertisementStatus status, Pageable pageable);

    @Query("SELECT a FROM Advertisement a WHERE a.status = com.doubleclick.wadii.advertisement.AdvertisementStatus.ACTIVE "
            + "AND (a.startAt IS NULL OR a.startAt <= :now) AND (a.endAt IS NULL OR a.endAt > :now)")
    Page<Advertisement> findLive(@Param("now") Instant now, Pageable pageable);

    /** ACTIVE ads past their end become EXPIRED. INACTIVE ads were disabled manually and are never touched. */
    @Modifying
    @Query("UPDATE Advertisement a SET a.status = com.doubleclick.wadii.advertisement.AdvertisementStatus.EXPIRED, a.updatedAt = :now "
            + "WHERE a.status = com.doubleclick.wadii.advertisement.AdvertisementStatus.ACTIVE AND a.endAt IS NOT NULL AND a.endAt <= :now")
    int expireEnded(@Param("now") Instant now);

    @Modifying
    @Query("UPDATE Advertisement a SET a.impressions = a.impressions + 1 WHERE a.id = :id")
    int incrementImpressions(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Advertisement a SET a.clicks = a.clicks + 1 WHERE a.id = :id")
    int incrementClicks(@Param("id") Long id);

    /** Portable SQL (PostgreSQL and MySQL/MariaDB); callers fall back to {@link #insertDaily} when no row was updated. */
    @Modifying
    @Query(value = "UPDATE advertisement_daily_stats SET impressions = impressions + :imp, clicks = clicks + :clk "
            + "WHERE advertisement_id = :id AND day = :day", nativeQuery = true)
    int incrementDaily(@Param("id") Long id, @Param("day") LocalDate day, @Param("imp") long impressions, @Param("clk") long clicks);

    @Modifying
    @Query(value = "INSERT INTO advertisement_daily_stats (advertisement_id, day, impressions, clicks) VALUES (:id, :day, :imp, :clk)",
            nativeQuery = true)
    int insertDaily(@Param("id") Long id, @Param("day") LocalDate day, @Param("imp") long impressions, @Param("clk") long clicks);

    @Query(value = "SELECT day, impressions, clicks FROM advertisement_daily_stats WHERE advertisement_id = :id "
            + "AND day >= :from ORDER BY day", nativeQuery = true)
    java.util.List<Object[]> dailyStats(@Param("id") Long id, @Param("from") LocalDate from);
}
