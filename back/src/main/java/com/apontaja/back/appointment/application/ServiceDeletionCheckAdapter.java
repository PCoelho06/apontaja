package com.apontaja.back.appointment.application;

import com.apontaja.back.appointment.domain.AppointmentRepository;
import com.apontaja.back.service.application.ServiceDeletionCheck;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Implémente le point d'extension défini par service.application (Phase 3
 * tranche 2) : une prestation ne peut être supprimée tant qu'un RDV actif
 * (ni annulé, ni supprimé) la référence.
 */
@Component
class ServiceDeletionCheckAdapter implements ServiceDeletionCheck {

    private final AppointmentRepository appointmentRepository;

    ServiceDeletionCheckAdapter(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    @Override
    public boolean hasBlockingAppointments(UUID serviceId) {
        return appointmentRepository.existsActiveByServiceId(serviceId);
    }
}
