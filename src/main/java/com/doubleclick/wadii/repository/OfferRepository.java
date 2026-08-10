package com.doubleclick.wadii.repository;

import com.doubleclick.wadii.entities.Offer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OfferRepository extends JpaRepository<Offer, Long> {

    @Query("SELECT DISTINCT o FROM Offer o JOIN o.services s WHERE s.id = :serviceId")
    List<Offer> findAllByServiceId(@Param("serviceId") Long serviceId);

    @Query("SELECT DISTINCT o FROM Offer o JOIN o.services s WHERE s.id IN :serviceIds")
    List<Offer> findAllByServiceIdIn(@Param("serviceIds") List<Long> serviceIds);

    @Query("SELECT o FROM Offer o WHERE LOWER(o.title) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(o.description) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<Offer> search(@Param("q") String q);

}
