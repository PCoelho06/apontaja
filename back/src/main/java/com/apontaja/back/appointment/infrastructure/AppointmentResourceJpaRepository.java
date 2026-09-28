package com.apontaja.back.appointment.infrastructure;

import com.apontaja.back.appointment.domain.AppointmentResource;
import com.apontaja.back.appointment.domain.AppointmentResourceId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface AppointmentResourceJpaRepository extends JpaRepository<AppointmentResource, AppointmentResourceId> {

    @Query("SELECT ar FROM AppointmentResource ar WHERE ar.id.appointmentId = :appointmentId")
    List<AppointmentResource> findByAppointmentId(@Param("appointmentId") UUID appointmentId);

    @Query("SELECT ar FROM AppointmentResource ar WHERE ar.id.appointmentId IN :appointmentIds")
    List<AppointmentResource> findByAppointmentIds(@Param("appointmentIds") Collection<UUID> appointmentIds);

    /**
     * is_active n'est pas mappé côté JPA (piloté par trigger, voir
     * AppointmentResource) — requête native nécessaire pour l'atteindre.
     */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM appointment_resource "
            + "WHERE resource_id = :resourceId AND is_active = true)", nativeQuery = true)
    boolean existsActiveByResourceId(@Param("resourceId") UUID resourceId);
}
