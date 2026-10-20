package com.apontaja.back.appointment.domain;

import java.util.Collection;
import java.util.UUID;

/** Réaffectation de RDV d'un client à un autre (fusion de profils). */
public interface AppointmentReassignmentRepository {

    /** Tous les RDV du salon (tous statuts, supprimés compris) des profils source passent au profil cible. */
    int reassignCustomer(UUID salonId, Collection<UUID> fromProfileIds, UUID toProfileId);
}
