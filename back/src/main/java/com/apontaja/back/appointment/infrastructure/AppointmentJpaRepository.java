package com.apontaja.back.appointment.infrastructure;

import com.apontaja.back.appointment.domain.Appointment;
import com.apontaja.back.appointment.domain.AppointmentStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface AppointmentJpaRepository extends JpaRepository<Appointment, UUID> {

    Optional<Appointment> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByServiceIdAndDeletedAtIsNullAndStatusNot(UUID serviceId, AppointmentStatus status);
}
