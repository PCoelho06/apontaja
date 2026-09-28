package com.apontaja.back.appointment.infrastructure;

import com.apontaja.back.appointment.domain.Appointment;
import com.apontaja.back.appointment.domain.AppointmentRepository;
import com.apontaja.back.appointment.domain.AppointmentStatus;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class AppointmentRepositoryAdapter implements AppointmentRepository {

    private final AppointmentJpaRepository jpaRepository;

    AppointmentRepositoryAdapter(AppointmentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Appointment save(Appointment appointment) {
        return jpaRepository.saveAndFlush(appointment);
    }

    @Override
    public Optional<Appointment> findAliveById(UUID id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public boolean existsActiveByServiceId(UUID serviceId) {
        return jpaRepository.existsByServiceIdAndDeletedAtIsNullAndStatusNot(serviceId, AppointmentStatus.CANCELLED);
    }

    @Override
    public List<Appointment> findOverlappingBySalonId(UUID salonId, UUID resourceId, Instant from, Instant to) {
        return resourceId == null ? jpaRepository.findOverlappingBySalonId(salonId, from, to)
                : jpaRepository.findOverlappingBySalonIdAndResourceId(salonId, resourceId, from, to);
    }
}
