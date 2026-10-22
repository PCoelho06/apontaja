package com.apontaja.back.appointment.infrastructure;

import com.apontaja.back.appointment.domain.Appointment;
import com.apontaja.back.appointment.domain.AppointmentStatus;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/** Lecture seule. */
interface BookingQuotaJpaRepository extends Repository<Appointment, UUID> {

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.salonId = :salonId AND a.customerProfileId = :profileId "
            + "AND a.deletedAt IS NULL AND a.status IN :statuses AND a.startAt > :now")
    long countUpcoming(@Param("salonId") UUID salonId, @Param("profileId") UUID profileId,
            @Param("statuses") Collection<AppointmentStatus> statuses, @Param("now") Instant now);
}
