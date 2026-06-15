package com.doubleclick.wadii.repository;

import com.doubleclick.wadii.entities.Offer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OfferRepository extends JpaRepository<Offer, Long> {

    @Query("SELECT DISTINCT o FROM Offer o JOIN o.services s WHERE s.id = :serviceId")
    List<Offer> findAllByServiceId(@Param("serviceId") Long serviceId);

}
