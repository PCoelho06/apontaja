package com.apontaja.back.service.application;

import java.util.UUID;

/**
 * Point d'extension : appointment (tranche 6) l'implémente pour bloquer la
 * suppression d'un service ayant encore des RDV actifs (ni annulés, ni
 * terminés). Même principe que ResourceDeletionCheck.
 */
public interface ServiceDeletionCheck {

    boolean hasBlockingAppointments(UUID serviceId);
}
