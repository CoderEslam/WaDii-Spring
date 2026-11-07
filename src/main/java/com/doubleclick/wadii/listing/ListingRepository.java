package com.doubleclick.wadii.listing;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ListingRepository extends JpaRepository<Listing, Long> {

    @Query("""
            select l from Listing l
            where l.status = com.doubleclick.wadii.listing.Listing.Status.ACTIVE
              and (:category is null or l.category = :category)
              and (:areaId is null or l.areaId = :areaId)
              and (:q is null or lower(l.title) like :q or lower(l.description) like :q)
              and (:minPrice is null or l.price >= :minPrice)
              and (:maxPrice is null or l.price <= :maxPrice)
            order by l.publishedAt desc
            """)
    Page<Listing> browse(@Param("category") ListingCategory category, @Param("areaId") Long areaId, @Param("q") String q,
                         @Param("minPrice") BigDecimal minPrice, @Param("maxPrice") BigDecimal maxPrice, Pageable pageable);

    @Query("select l from Listing l where l.sellerId = :sellerId and (:status is null or l.status = :status) order by l.id desc")
    Page<Listing> mine(@Param("sellerId") Long sellerId, @Param("status") Listing.Status status, Pageable pageable);

    @Query("select l from Listing l where (:status is null or l.status = :status) order by l.id desc")
    Page<Listing> adminSearch(@Param("status") Listing.Status status, Pageable pageable);

    long countBySellerIdAndStatusIn(Long sellerId, Collection<Listing.Status> statuses);

    List<Listing> findBySellerIdAndStatusAndTitleIgnoreCase(Long sellerId, Listing.Status status, String title);

    @Query("select l.id from Listing l where l.status = com.doubleclick.wadii.listing.Listing.Status.ACTIVE and l.expiresAt <= :now")
    List<Long> expired(@Param("now") Instant now);
}
