package com.doubleclick.wadii.appointment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    Optional<Appointment> findByOrderId(Long orderId);
}
