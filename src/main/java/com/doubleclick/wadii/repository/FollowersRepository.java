package com.doubleclick.wadii.repository;

import com.doubleclick.wadii.entities.Follower;
import com.doubleclick.wadii.entities.FollowerId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowersRepository extends JpaRepository<Follower, FollowerId> {

}
