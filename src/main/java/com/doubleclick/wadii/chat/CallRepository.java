package com.doubleclick.wadii.chat;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CallRepository extends JpaRepository<Call, Long> {

    Page<Call> findByConversationId(Long conversationId, Pageable pageable);

    Optional<Call> findFirstByChannelNameAndStatusInOrderByStartedAtDesc(String channelName, java.util.Collection<CallStatus> statuses);
}
