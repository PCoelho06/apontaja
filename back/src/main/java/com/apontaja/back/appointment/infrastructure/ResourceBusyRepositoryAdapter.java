package com.apontaja.back.appointment.infrastructure;

import com.apontaja.back.appointment.domain.AppointmentStatus;
import com.apontaja.back.appointment.domain.BusyInterval;
import com.apontaja.back.appointment.domain.ResourceBusyRepository;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
class ResourceBusyRepositoryAdapter implements ResourceBusyRepository {

    private final ResourceBusyJpaRepository jpaRepository;

    ResourceBusyRepositoryAdapter(ResourceBusyJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<BusyInterval> findActiveIntervals(Collection<UUID> resourceIds, Instant from, Instant to) {
        if (resourceIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findActiveIntervals(resourceIds, AppointmentStatus.CANCELLED, from, to);
    }
}
