package com.apontaja.back.appointment.application;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.apontaja.back.appointment.domain.AppointmentStatus;
import com.apontaja.back.resource.application.BlockingAppointment;
import com.apontaja.back.resource.application.ClosureConflictCheck;

/**
 * Implémente le point d'extension défini par resource.application (tranche 8b) :
 * une fermeture ne peut pas recouvrir un RDV SCHEDULED ou CONFIRMED. Les RDV
 * annulés, terminés ou no-show ne bloquent pas (faits passés ou créneau libéré).
 * S'appuie sur l'agenda ({@link AppointmentQueryService#list}), qui filtre déjà
 * par recouvrement et par ressource optionnelle ; le filtre de statut est fait
 * ici.
 */
@Component
class ClosureConflictCheckAdapter implements ClosureConflictCheck {

    private static final Set<String> BLOCKING_STATUSES = Set.of(AppointmentStatus.SCHEDULED.name(),
            AppointmentStatus.CONFIRMED.name());

    private final AppointmentQueryService queryService;

    ClosureConflictCheckAdapter(AppointmentQueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    public List<BlockingAppointment> findBlockingAppointments(UUID salonId, UUID resourceId, Instant start,
            Instant end) {
        return queryService.list(salonId, resourceId, start.toString(), end.toString()).stream()
                .filter(a -> BLOCKING_STATUSES.contains(a.status()))
                .map(a -> new BlockingAppointment(a.appointmentId(), a.resourceId(), a.startAt(), a.endAt()))
                .toList();
    }
}
