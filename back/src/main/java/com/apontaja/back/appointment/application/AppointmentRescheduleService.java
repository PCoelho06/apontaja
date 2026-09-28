package com.apontaja.back.appointment.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.apontaja.back.appointment.domain.Appointment;
import com.apontaja.back.appointment.domain.AppointmentRepository;
import com.apontaja.back.appointment.domain.AppointmentStatus;
import com.apontaja.back.resource.application.AvailabilityCheck;
import com.apontaja.back.resource.application.AvailabilityService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Déplacement d'un RDV (Phase 3, tranche 8). Revalidation complète, ce qui
 * ferme le bug historique "aucune revalidation à la mise à jour" : RDV du
 * salon (404), statut éligible SCHEDULED/CONFIRMED (409), disponibilité
 * horaires/fermetures de la ressource du RDV (409), puis persistance — la
 * contrainte d'exclusion PostgreSQL (SQLSTATE 23P01, levée par le trigger
 * AFTER UPDATE) reste la dernière défense contre un chevauchement concurrent.
 *
 * <p>
 * Le snapshot prix/durée est conservé : la fin est recalculée à partir de la
 * durée convenue à la réservation, pas de la durée courante de la prestation.
 */
@Service
public class AppointmentRescheduleService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentQueryService queryService;
    private final AvailabilityService availabilityService;

    AppointmentRescheduleService(AppointmentRepository appointmentRepository,
            AppointmentQueryService queryService, AvailabilityService availabilityService) {
        this.appointmentRepository = appointmentRepository;
        this.queryService = queryService;
        this.availabilityService = availabilityService;
    }

    @Transactional
    public AppointmentSummary reschedule(UUID salonId, UUID appointmentId, String startAt) {
        Instant newStart = AppointmentCreationService.parseStartAt(startAt);

        UUID resourceId = queryService.findAliveInSalon(salonId, appointmentId)
                .orElseThrow(AppointmentNotFoundException::new).resourceId();
        Appointment appointment = appointmentRepository.findAliveById(appointmentId)
                .orElseThrow(AppointmentNotFoundException::new);

        AppointmentStatus status = appointment.getStatus();
        if (status != AppointmentStatus.SCHEDULED && status != AppointmentStatus.CONFIRMED) {
            throw new AppointmentNotReschedulableException(status);
        }

        Instant newEnd = newStart.plus(appointment.getDurationAtBookingMinutes(), ChronoUnit.MINUTES);

        AvailabilityCheck availability = availabilityService.check(salonId, resourceId, newStart, newEnd);
        if (!availability.available()) {
            throw new AppointmentNotAvailableException(availability.issues());
        }

        appointment.reschedule(newStart, newEnd);
        try {
            appointment = appointmentRepository.save(appointment);
        } catch (DataIntegrityViolationException e) {
            throw new AppointmentConflictException();
        }

        return AppointmentSummary.of(appointment, resourceId);
    }
}
