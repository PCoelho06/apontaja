package com.apontaja.back.appointment.infrastructure;

import com.apontaja.back.appointment.domain.Appointment;
import com.apontaja.back.appointment.domain.AppointmentStatus;
import com.apontaja.back.appointment.domain.BusyInterval;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Lecture seule. Jointure « à l'ancienne » (produit cartésien + WHERE) vers AppointmentResource, qui
 * ne mappe que sa clé composite : les plages viennent de l'Appointment.
 */
interface ResourceBusyJpaRepository extends Repository<Appointment, UUID> {

    @Query("SELECT new com.apontaja.back.appointment.domain.BusyInterval(ar.id.resourceId, a.startAt, a.endAt) "
            + "FROM Appointment a, AppointmentResource ar "
            + "WHERE ar.id.appointmentId = a.id AND ar.id.resourceId IN :resourceIds "
            + "AND a.deletedAt IS NULL AND a.status <> :excluded AND a.startAt < :to AND a.endAt > :from "
            + "ORDER BY a.startAt")
    List<BusyInterval> findActiveIntervals(@Param("resourceIds") Collection<UUID> resourceIds,
            @Param("excluded") AppointmentStatus excluded, @Param("from") Instant from, @Param("to") Instant to);
}
