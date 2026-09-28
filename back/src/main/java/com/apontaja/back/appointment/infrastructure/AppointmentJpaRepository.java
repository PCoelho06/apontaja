package com.apontaja.back.appointment.infrastructure;

import com.apontaja.back.appointment.domain.Appointment;
import com.apontaja.back.appointment.domain.AppointmentStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AppointmentJpaRepository extends JpaRepository<Appointment, UUID> {

    Optional<Appointment> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByServiceIdAndDeletedAtIsNullAndStatusNot(UUID serviceId, AppointmentStatus status);

    @Query("SELECT a FROM Appointment a WHERE a.salonId = :salonId AND a.deletedAt IS NULL "
            + "AND a.startAt < :to AND a.endAt > :from ORDER BY a.startAt")
    List<Appointment> findOverlappingBySalonId(@Param("salonId") UUID salonId, @Param("from") Instant from,
            @Param("to") Instant to);

    /** La jointure vers AppointmentResource reste dans ce domaine (appointment), donc autorisée en
     * JPQL même si elle n'est pas un @ManyToOne mappé. Deux requêtes distinctes plutôt qu'un
     * ":resourceId IS NULL OR ..." : évite le paramètre nul non typé, piège classique sur PostgreSQL. */
    @Query("SELECT a FROM Appointment a WHERE a.salonId = :salonId AND a.deletedAt IS NULL "
            + "AND a.startAt < :to AND a.endAt > :from AND a.id IN "
            + "(SELECT ar.id.appointmentId FROM AppointmentResource ar WHERE ar.id.resourceId = :resourceId) "
            + "ORDER BY a.startAt")
    List<Appointment> findOverlappingBySalonIdAndResourceId(@Param("salonId") UUID salonId,
            @Param("resourceId") UUID resourceId, @Param("from") Instant from, @Param("to") Instant to);
}
