package com.doubleclick.wadii.chat;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface MessageRepository extends JpaRepository<Message, Long> {

    Page<Message> findByConversationId(Long conversationId, Pageable pageable);

    @Modifying
    @Query("UPDATE Message m SET m.readAt = :now WHERE m.conversationId = :conversationId "
            + "AND m.readAt IS NULL AND (m.senderId IS NULL OR m.senderId <> :readerId)")
    int markRead(@Param("conversationId") Long conversationId, @Param("readerId") Long readerId, @Param("now") Instant now);

    @Query("SELECT COUNT(m) FROM Message m, Conversation c WHERE m.conversationId = c.id "
            + "AND (c.customerId = :userId OR c.providerUserId = :userId) AND m.readAt IS NULL "
            + "AND (m.senderId IS NULL OR m.senderId <> :userId)")
    long countUnread(@Param("userId") Long userId);
}
