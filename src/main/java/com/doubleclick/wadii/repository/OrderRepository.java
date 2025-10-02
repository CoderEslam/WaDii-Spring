package com.doubleclick.wadii.repository;

import com.doubleclick.wadii.entities.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

}
