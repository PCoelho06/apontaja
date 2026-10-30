package com.apontaja.back.appointment.infrastructure;

import com.apontaja.back.appointment.domain.Appointment;
import com.apontaja.back.appointment.domain.AppointmentStatus;
import com.apontaja.back.appointment.domain.PendingRequest;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lecture seule. Équivalent SQL de {@code RequestExpiryPolicy.isDue} : min(A, B, C) &lt;= now si et seulement si
 * A &lt;= now ou C &lt;= now ou B &lt;= now, avec B = max(début - 24 h, création + 4 h).
 */
interface AppointmentExpiryJpaRepository extends Repository<Appointment, UUID> {

    @Query("SELECT new com.apontaja.back.appointment.domain.PendingRequest(a.id, a.salonId) FROM Appointment a "
            + "WHERE a.status = :status AND a.deletedAt IS NULL AND (a.createdAt <= :created48 "
            + "OR a.startAt <= :now OR (a.startAt <= :startLimit AND a.createdAt <= :created4)) "
            + "ORDER BY a.createdAt ASC, a.id ASC")
    List<PendingRequest> findDue(@Param("status") AppointmentStatus status, @Param("now") Instant now,
            @Param("created48") Instant created48, @Param("created4") Instant created4,
            @Param("startLimit") Instant startLimit, Pageable pageable);
}
