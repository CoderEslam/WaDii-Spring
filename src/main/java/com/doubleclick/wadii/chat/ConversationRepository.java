package com.doubleclick.wadii.chat;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByKindAndRefIdAndUserAAndUserB(Conversation.Kind kind, Long refId, Long userA, Long userB);

    @Query("""
            select c from Conversation c where c.userA = :userId or c.userB = :userId
            order by case when c.lastMessageAt is null then 1 else 0 end, c.lastMessageAt desc, c.id desc
            """)
    Page<Conversation> forUser(@Param("userId") Long userId, Pageable pageable);
}
