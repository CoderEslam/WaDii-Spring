package com.doubleclick.wadii.repository;

import com.doubleclick.wadii.entities.Follower;
import com.doubleclick.wadii.entities.FollowerId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FollowRepository extends JpaRepository<Follower, FollowerId> {

    List<Follower> findByIdUserId(Long userId);

    List<Follower> findByIdProviderId(Long providerId);

    boolean existsByIdUserIdAndIdProviderId(Long userId, Long providerId);

    long countByIdProviderId(Long providerId);

//    @Modifying
//    @Query("DELETE FROM Follow f WHERE f.id.userId = :userId AND f.id.providerId = :providerId")
//    void deleteByUserAndProvider(@Param("userId") Long userId,
//                                 @Param("providerId") Long providerId);
}
