package com.doubleclick.wadii.repository;

import com.doubleclick.wadii.entities.UserNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {

    List<UserNotification> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<UserNotification> findByUserIdAndIsReadOrderByCreatedAtDesc(Long userId, Boolean isRead);

    long countByUserIdAndIsRead(Long userId, Boolean isRead);
}
