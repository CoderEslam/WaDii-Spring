package com.doubleclick.wadii.chat;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("select m from Message m where m.conversationId = :conversationId and m.id > :afterId order by m.id asc")
    List<Message> after(@Param("conversationId") Long conversationId, @Param("afterId") long afterId, Pageable pageable);

    Optional<Message> findFirstByConversationIdOrderByIdDesc(Long conversationId);

    @Query("select count(m) from Message m where m.conversationId = :conversationId and m.id > :afterId and m.senderId <> :userId")
    long countUnread(@Param("conversationId") Long conversationId, @Param("afterId") long afterId, @Param("userId") Long userId);
}
