package com.doubleclick.wadii.appointment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppointmentChangeRepository extends JpaRepository<AppointmentChange, Long> {

    List<AppointmentChange> findByAppointmentIdOrderByCreatedAtAsc(Long appointmentId);
}
