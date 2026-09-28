package com.apontaja.back.appointment.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository {

    /** Flush immédiat, cohérent avec le reste du repo — sert aussi bien à la création qu'aux
     * transitions de statut (confirm/cancel/complete/no-show). */
    Appointment save(Appointment appointment);

    Optional<Appointment> findAliveById(UUID id);

    /** "Actif" = ni annulé ni supprimé — utilisé par ServiceDeletionCheck. */
    boolean existsActiveByServiceId(UUID serviceId);

    /**
     * RDV du salon recouvrant [from, to), triés par début. resourceId optionnel :
     * s'il est fourni, ne renvoie que les RDV de cette ressource (v1 : une seule
     * ressource par RDV, cf. AppointmentResource).
     */
    List<Appointment> findOverlappingBySalonId(UUID salonId, UUID resourceId, Instant from, Instant to);
}
