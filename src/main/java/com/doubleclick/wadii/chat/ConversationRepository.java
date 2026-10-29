package com.doubleclick.wadii.chat;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByOrderId(Long orderId);

    @Query("SELECT c FROM Conversation c WHERE c.customerId = :userId OR c.providerUserId = :userId")
    Page<Conversation> findForUser(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT DISTINCT CASE WHEN c.customerId = :userId THEN c.providerUserId ELSE c.customerId END "
            + "FROM Conversation c WHERE c.customerId = :userId OR c.providerUserId = :userId")
    List<Long> findCounterpartIds(@Param("userId") Long userId);
}
