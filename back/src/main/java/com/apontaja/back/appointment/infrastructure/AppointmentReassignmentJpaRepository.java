package com.apontaja.back.appointment.infrastructure;

import com.apontaja.back.appointment.domain.Appointment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.UUID;

interface AppointmentReassignmentJpaRepository extends JpaRepository<Appointment, UUID> {

    /**
     * SQL natif : {@code customer_profile_id} est {@code updatable = false} côté entité. Aucun trigger n'écoute
     * cette colonne.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE appointment SET customer_profile_id = :toId "
            + "WHERE salon_id = :salonId AND customer_profile_id IN (:fromIds)", nativeQuery = true)
    int reassign(@Param("salonId") UUID salonId, @Param("fromIds") Collection<UUID> fromIds,
            @Param("toId") UUID toId);
}
