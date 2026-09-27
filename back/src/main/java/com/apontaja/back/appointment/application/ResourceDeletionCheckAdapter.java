package com.apontaja.back.appointment.application;

import com.apontaja.back.appointment.domain.AppointmentResourceRepository;
import com.apontaja.back.resource.application.ResourceDeletionCheck;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Implémente le point d'extension défini par resource.application (Phase 3
 * tranche 1) : une ressource ne peut être supprimée tant qu'un RDV actif
 * (ni annulé, ni supprimé) la référence. S'appuie sur is_active, colonne
 * pilotée par trigger (voir AppointmentResource).
 */
@Component
class ResourceDeletionCheckAdapter implements ResourceDeletionCheck {

    private final AppointmentResourceRepository appointmentResourceRepository;

    ResourceDeletionCheckAdapter(AppointmentResourceRepository appointmentResourceRepository) {
        this.appointmentResourceRepository = appointmentResourceRepository;
    }

    @Override
    public boolean hasBlockingAppointments(UUID resourceId) {
        return appointmentResourceRepository.existsActiveByResourceId(resourceId);
    }
}
