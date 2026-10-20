package com.apontaja.back.customer.application;

import java.util.Collection;
import java.util.UUID;

/** Port implémenté dans {@code appointment.application} : rattache les RDV d'un salon à un autre profil. */
public interface CustomerAppointmentReassignment {

    /** Réaffecte tous les RDV (tous statuts) des profils {@code fromProfileIds} ; renvoie leur nombre. */
    int reassign(UUID salonId, Collection<UUID> fromProfileIds, UUID toProfileId);
}
