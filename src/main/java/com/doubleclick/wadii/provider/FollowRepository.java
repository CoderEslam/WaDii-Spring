package com.doubleclick.wadii.provider;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowRepository extends JpaRepository<Follow, Follow.FollowId> {

    Page<Follow> findByIdProviderId(Long providerId, Pageable pageable);

    Page<Follow> findByIdUserId(Long userId, Pageable pageable);

    long countByIdProviderId(Long providerId);
}
