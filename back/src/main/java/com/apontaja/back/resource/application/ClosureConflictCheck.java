package com.apontaja.back.resource.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Point d'extension : le domaine appointment l'implémente pour signaler les
 * RDV actifs (SCHEDULED/CONFIRMED) recouvrant une fermeture projetée. Même
 * inversion de dépendance que {@link ResourceDeletionCheck} : resource ne peut
 * pas dépendre d'appointment (graphe ArchUnit).
 *
 * <p>
 * {@code resourceId} null = fermeture du salon (RDV de toutes les ressources) ;
 * sinon uniquement les RDV de cette ressource. Recouvrement sur [start, end),
 * bornes adjacentes exclues.
 */
public interface ClosureConflictCheck {

    List<BlockingAppointment> findBlockingAppointments(UUID salonId, UUID resourceId, Instant start, Instant end);
}
