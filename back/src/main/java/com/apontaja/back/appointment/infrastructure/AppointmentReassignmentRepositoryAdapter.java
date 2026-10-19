package com.apontaja.back.appointment.infrastructure;

import com.apontaja.back.appointment.domain.AppointmentReassignmentRepository;

import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.UUID;

@Repository
class AppointmentReassignmentRepositoryAdapter implements AppointmentReassignmentRepository {

    private final AppointmentReassignmentJpaRepository jpaRepository;

    AppointmentReassignmentRepositoryAdapter(AppointmentReassignmentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public int reassignCustomer(UUID salonId, Collection<UUID> fromProfileIds, UUID toProfileId) {
        if (fromProfileIds.isEmpty()) {
            return 0;
        }
        return jpaRepository.reassign(salonId, fromProfileIds, toProfileId);
    }
}
