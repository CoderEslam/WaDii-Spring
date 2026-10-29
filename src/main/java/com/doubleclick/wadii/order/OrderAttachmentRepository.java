package com.doubleclick.wadii.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderAttachmentRepository extends JpaRepository<OrderAttachment, Long> {

    List<OrderAttachment> findByOrderIdOrderById(Long orderId);

    long countByOrderId(Long orderId);
}
