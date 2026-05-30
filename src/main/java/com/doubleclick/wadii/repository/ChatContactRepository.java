package com.doubleclick.wadii.repository;

import com.doubleclick.wadii.entities.ChatContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatContactRepository extends JpaRepository<ChatContact, Long> {

    List<ChatContact> findByUserIdOrderByLastMessageAtDesc(Long userId);

    Optional<ChatContact> findByUserIdAndContactId(Long userId, Long contactId);
}