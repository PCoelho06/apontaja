package com.apontaja.back.appointment.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AppointmentResourceRepository {

    /** Flush immédiat : une violation d'exclusion (23P01) remonte ici, pas au commit. */
    AppointmentResource save(AppointmentResource link);

    /**
     * "Actif" au sens de la colonne is_active (pilotée par trigger depuis
     * appointment.status/deleted_at) — utilisé par ResourceDeletionCheck.
     */
    boolean existsActiveByResourceId(UUID resourceId);

    /** v1 : toujours au plus une ressource par RDV (invariant applicatif, pas une contrainte DB). */
    List<UUID> findResourceIdsByAppointmentId(UUID appointmentId);

    /** Version en lot de findResourceIdsByAppointmentId, pour éviter le N+1 sur une liste (agenda). */
    List<AppointmentResource> findByAppointmentIds(Collection<UUID> appointmentIds);
}
