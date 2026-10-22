package com.apontaja.back.shared.domain;

import java.util.UUID;

/**
 * Verrou de transaction par salon, partagé par tout ce qui lit puis modifie la disponibilité d'un salon
 * (réservation en ligne, création/déplacement de RDV par le staff, création/modification de fermeture).
 * Il sérialise ces opérations : sans lui, une fermeture et une réservation concurrentes pourraient chacune
 * valider leur vérification sans voir l'autre. Réentrant dans une même transaction, levé au commit/rollback ;
 * à n'appeler que dans une transaction ouverte.
 */
public interface SalonSchedulingLock {

    void lock(UUID salonId);
}
