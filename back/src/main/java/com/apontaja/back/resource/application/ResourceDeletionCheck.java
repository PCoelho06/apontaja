package com.apontaja.back.resource.application;

import java.util.UUID;

/**
 * Point d'extension : un autre domaine (appointment, tranche 6) l'implémente
 * pour bloquer la suppression d'une ressource ayant encore des RDV actifs
 * (ni annulés, ni terminés). Inversion de dépendance volontaire : resource ne
 * peut pas dépendre d'appointment (graphe ArchUnit), mais appointment dépend
 * de resource.application.
 */
public interface ResourceDeletionCheck {

    boolean hasBlockingAppointments(UUID resourceId);
}
