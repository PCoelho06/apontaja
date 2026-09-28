package com.apontaja.back.appointment.application;

import com.apontaja.back.appointment.domain.Appointment;
import com.apontaja.back.appointment.domain.AppointmentRepository;
import com.apontaja.back.appointment.domain.AppointmentResourceRepository;
import com.apontaja.back.appointment.domain.AppointmentStatus;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Transitions de statut d'un RDV. Éligibilité vérifiée ici (pas dans
 * l'entité, mutateurs inconditionnels) — même principe que
 * StaffMembershipManagementService pour StaffMembership.changeRole.
 *
 * <p>
 * Matrice (Q4, confirmée) : confirm SCHEDULED -&gt; CONFIRMED ; cancel
 * SCHEDULED/CONFIRMED -&gt; CANCELLED ; complete CONFIRMED -&gt; COMPLETED
 * (jamais directement depuis SCHEDULED, lecture stricte de la chaîne
 * SCHEDULED -&gt; CONFIRMED -&gt; COMPLETED) ; no-show SCHEDULED/CONFIRMED -&gt;
 * NO_SHOW, seulement à partir de l'heure de début.
 */
@Service
public class AppointmentStatusService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentResourceRepository appointmentResourceRepository;
    private final Clock clock;

    AppointmentStatusService(AppointmentRepository appointmentRepository,
            AppointmentResourceRepository appointmentResourceRepository, Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.appointmentResourceRepository = appointmentResourceRepository;
        this.clock = clock;
    }

    @Transactional
    public AppointmentSummary confirm(UUID salonId, UUID appointmentId) {
        Appointment appointment = requireInSalon(salonId, appointmentId);
        requireStatus(appointment, "confirmer", AppointmentStatus.SCHEDULED);
        appointment.confirm();
        return summarize(appointmentRepository.save(appointment));
    }

    @Transactional
    public AppointmentSummary cancel(UUID salonId, UUID appointmentId, UUID cancelledBy, String reason) {
        Appointment appointment = requireInSalon(salonId, appointmentId);
        requireStatus(appointment, "annuler", AppointmentStatus.SCHEDULED, AppointmentStatus.CONFIRMED);
        appointment.cancel(clock.instant(), cancelledBy, normalize(reason));
        return summarize(appointmentRepository.save(appointment));
    }

    @Transactional
    public AppointmentSummary complete(UUID salonId, UUID appointmentId) {
        Appointment appointment = requireInSalon(salonId, appointmentId);
        requireStatus(appointment, "terminer", AppointmentStatus.CONFIRMED);
        appointment.complete();
        return summarize(appointmentRepository.save(appointment));
    }

    @Transactional
    public AppointmentSummary markNoShow(UUID salonId, UUID appointmentId) {
        Appointment appointment = requireInSalon(salonId, appointmentId);
        requireStatus(appointment, "marquer absent", AppointmentStatus.SCHEDULED, AppointmentStatus.CONFIRMED);
        if (clock.instant().isBefore(appointment.getStartAt())) {
            throw new InvalidAppointmentTransitionException(
                    "Impossible de marquer un client absent avant l'heure du rendez-vous.");
        }
        appointment.markNoShow();
        return summarize(appointmentRepository.save(appointment));
    }

    private Appointment requireInSalon(UUID salonId, UUID appointmentId) {
        return appointmentRepository.findAliveById(appointmentId).filter(a -> a.getSalonId().equals(salonId))
                .orElseThrow(AppointmentNotFoundException::new);
    }

    private static void requireStatus(Appointment appointment, String action, AppointmentStatus... allowed) {
        for (AppointmentStatus status : allowed) {
            if (appointment.getStatus() == status) {
                return;
            }
        }
        throw new InvalidAppointmentTransitionException(
                "Impossible de " + action + " un rendez-vous " + appointment.getStatus() + ".");
    }

    private AppointmentSummary summarize(Appointment appointment) {
        UUID resourceId = appointmentResourceRepository.findResourceIdsByAppointmentId(appointment.getId()).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Aucune ressource associée au rendez-vous " + appointment.getId() + "."));
        return AppointmentSummary.of(appointment, resourceId);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
