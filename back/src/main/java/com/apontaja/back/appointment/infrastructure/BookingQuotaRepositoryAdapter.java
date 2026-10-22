package com.apontaja.back.appointment.infrastructure;

import com.apontaja.back.appointment.domain.AppointmentStatus;
import com.apontaja.back.appointment.domain.BookingQuotaRepository;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
class BookingQuotaRepositoryAdapter implements BookingQuotaRepository {

    private static final List<AppointmentStatus> ACTIVE = List.of(AppointmentStatus.SCHEDULED,
            AppointmentStatus.CONFIRMED);

    private final BookingQuotaJpaRepository jpaRepository;

    BookingQuotaRepositoryAdapter(BookingQuotaJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public int countUpcomingActive(UUID salonId, UUID customerProfileId, Instant now) {
        return (int) jpaRepository.countUpcoming(salonId, customerProfileId, ACTIVE, now);
    }
}
