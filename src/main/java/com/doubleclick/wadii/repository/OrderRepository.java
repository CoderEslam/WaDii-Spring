package com.doubleclick.wadii.repository;

import com.doubleclick.wadii.entities.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<List<Order>> findOrderByUserId(Long userId);

}
