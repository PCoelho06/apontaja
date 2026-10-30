package com.apontaja.back.appointment.infrastructure;

import com.apontaja.back.appointment.domain.AppointmentExpiryRepository;
import com.apontaja.back.appointment.domain.AppointmentStatus;
import com.apontaja.back.appointment.domain.PendingRequest;
import com.apontaja.back.appointment.domain.RequestExpiryPolicy;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
class AppointmentExpiryRepositoryAdapter implements AppointmentExpiryRepository {

    private final AppointmentExpiryJpaRepository jpaRepository;

    AppointmentExpiryRepositoryAdapter(AppointmentExpiryJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<PendingRequest> findDue(Instant now, int limit) {
        return jpaRepository.findDue(AppointmentStatus.SCHEDULED, now, now.minus(RequestExpiryPolicy.MAX_WAIT),
                now.minus(RequestExpiryPolicy.MIN_WAIT), now.plus(RequestExpiryPolicy.LEAD),
                PageRequest.of(0, limit));
    }
}
