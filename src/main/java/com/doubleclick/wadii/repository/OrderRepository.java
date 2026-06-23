package com.doubleclick.wadii.repository;

import com.doubleclick.wadii.entities.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<List<Order>> findOrderByUserId(Long userId);

    @Query("SELECT DISTINCT o FROM Order o JOIN o.services s JOIN s.providers p WHERE p.id = :providerId")
    List<Order> findOrdersByProviderId(@Param("providerId") Long providerId);

    @Query("SELECT DISTINCT o FROM Order o JOIN o.services s JOIN o.user u WHERE u.id = :userId")
    List<Order> findOrdersByUserId(@Param("userId") Long userId);
}
