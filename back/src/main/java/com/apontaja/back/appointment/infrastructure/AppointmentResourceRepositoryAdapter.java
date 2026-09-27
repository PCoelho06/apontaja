package com.apontaja.back.appointment.infrastructure;

import com.apontaja.back.appointment.domain.AppointmentResource;
import com.apontaja.back.appointment.domain.AppointmentResourceRepository;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
class AppointmentResourceRepositoryAdapter implements AppointmentResourceRepository {

    private final AppointmentResourceJpaRepository jpaRepository;

    AppointmentResourceRepositoryAdapter(AppointmentResourceJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public AppointmentResource save(AppointmentResource link) {
        return jpaRepository.saveAndFlush(link);
    }

    @Override
    public boolean existsActiveByResourceId(UUID resourceId) {
        return jpaRepository.existsActiveByResourceId(resourceId);
    }

    @Override
    public List<UUID> findResourceIdsByAppointmentId(UUID appointmentId) {
        return jpaRepository.findByAppointmentId(appointmentId).stream().map(AppointmentResource::getResourceId)
                .toList();
    }
}
