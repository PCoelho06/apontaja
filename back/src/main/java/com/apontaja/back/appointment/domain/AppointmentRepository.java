package com.apontaja.back.appointment.domain;

import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository {

    /** Flush immédiat, cohérent avec le reste du repo. */
    Appointment save(Appointment appointment);

    Optional<Appointment> findAliveById(UUID id);

    /** "Actif" = ni annulé ni supprimé — utilisé par ServiceDeletionCheck. */
    boolean existsActiveByServiceId(UUID serviceId);
}
