package com.doubleclick.wadii.repository;

import com.doubleclick.wadii.entities.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByFromUserIdOrderByCreatedAtDesc(Long fromUserId);

    List<Message> findByToUserIdOrderByCreatedAtDesc(Long toUserId);

    // All messages between two users (a conversation), in chronological order.
    @Query("SELECT m FROM Message m " +
           "WHERE (m.fromUser.id = :u1 AND m.toUser.id = :u2) " +
           "   OR (m.fromUser.id = :u2 AND m.toUser.id = :u1) " +
           "ORDER BY m.createdAt ASC")
    Page<Message> findConversation(@Param("u1") Long user1Id,
                                   @Param("u2") Long user2Id,
                                   Pageable pageable);
}
