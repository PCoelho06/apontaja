package com.apontaja.back.appointment.application;

import com.apontaja.back.appointment.domain.AppointmentRepository;
import com.apontaja.back.appointment.domain.AppointmentResourceRepository;

import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class AppointmentQueryService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentResourceRepository appointmentResourceRepository;

    AppointmentQueryService(AppointmentRepository appointmentRepository,
            AppointmentResourceRepository appointmentResourceRepository) {
        this.appointmentRepository = appointmentRepository;
        this.appointmentResourceRepository = appointmentResourceRepository;
    }

    /** Vide si le RDV n'existe pas ou appartient à un autre salon. */
    public Optional<AppointmentSummary> findAliveInSalon(UUID salonId, UUID appointmentId) {
        return appointmentRepository.findAliveById(appointmentId).filter(a -> a.getSalonId().equals(salonId))
                .map(a -> AppointmentSummary.of(a, requireSoleResourceId(a.getId())));
    }

    /**
     * v1 : invariant "toujours exactement une ressource par RDV" garanti par
     * AppointmentCreationService — sa violation serait un bug de données, jamais
     * un cas utilisateur normal.
     */
    private UUID requireSoleResourceId(UUID appointmentId) {
        return appointmentResourceRepository.findResourceIdsByAppointmentId(appointmentId).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Aucune ressource associée au rendez-vous " + appointmentId + " : incohérence de données."));
    }
}
