package com.doubleclick.wadii.repository;

import com.doubleclick.wadii.entities.OrderCancel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderCancelRepository extends JpaRepository<OrderCancel, Long> {

}