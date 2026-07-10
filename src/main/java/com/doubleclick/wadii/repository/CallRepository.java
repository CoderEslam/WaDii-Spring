package com.doubleclick.wadii.repository;

import com.doubleclick.wadii.entities.Call;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CallRepository extends JpaRepository<Call, Long> {

    List<Call> findByCallerIdOrCalleeIdOrderByStartedAtDesc(Long callerId, Long calleeId);

    List<Call> findByRoomNameOrderByStartedAtDesc(String roomName);
}
