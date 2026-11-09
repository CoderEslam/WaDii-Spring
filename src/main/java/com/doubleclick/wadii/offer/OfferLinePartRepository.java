package com.doubleclick.wadii.offer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface OfferLinePartRepository extends JpaRepository<OfferLinePart, Long> {

    List<OfferLinePart> findByLineIdIn(Collection<Long> lineIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from OfferLinePart p where p.lineId in :lineIds")
    void deleteByLineIdIn(@Param("lineIds") Collection<Long> lineIds);
}
