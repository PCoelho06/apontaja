package com.apontaja.back.appointment.application;

import com.apontaja.back.appointment.domain.Appointment;
import com.apontaja.back.appointment.domain.AppointmentRepository;
import com.apontaja.back.appointment.domain.AppointmentResourceRepository;
import com.apontaja.back.appointment.application.ClientAppointmentNotification.Kind;
import com.apontaja.back.appointment.domain.AppointmentStatus;
import com.apontaja.back.shared.domain.SalonSchedulingLock;

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
 *
 * <p>
 * Chaque transition prend {@link SalonSchedulingLock} (sérialisée avec les réservations et annulations du client) ;
 * confirmer ou annuler publie un email au client ({@link StaffAppointmentNotifier}) : refus d'une demande
 * ({@code SCHEDULED}) ou annulation par le salon ({@code CONFIRMED}).
 */
@Service
public class AppointmentStatusService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentResourceRepository appointmentResourceRepository;
    private final Clock clock;
    private final SalonSchedulingLock schedulingLock;
    private final StaffAppointmentNotifier notifier;

    AppointmentStatusService(AppointmentRepository appointmentRepository,
            AppointmentResourceRepository appointmentResourceRepository, Clock clock,
            SalonSchedulingLock schedulingLock, StaffAppointmentNotifier notifier) {
        this.appointmentRepository = appointmentRepository;
        this.appointmentResourceRepository = appointmentResourceRepository;
        this.clock = clock;
        this.schedulingLock = schedulingLock;
        this.notifier = notifier;
    }

    @Transactional
    public AppointmentSummary confirm(UUID salonId, UUID appointmentId) {
        schedulingLock.lock(salonId);
        Appointment appointment = requireInSalon(salonId, appointmentId);
        requireStatus(appointment, "confirmer", AppointmentStatus.SCHEDULED);
        appointment.confirm();
        AppointmentSummary summary = summarize(appointmentRepository.save(appointment));
        notifier.notifyClient(Kind.CONFIRMED, summary);
        return summary;
    }

    @Transactional
    public AppointmentSummary cancel(UUID salonId, UUID appointmentId, UUID cancelledBy, String reason) {
        schedulingLock.lock(salonId);
        Appointment appointment = requireInSalon(salonId, appointmentId);
        requireStatus(appointment, "annuler", AppointmentStatus.SCHEDULED, AppointmentStatus.CONFIRMED);
        Kind kind = appointment.getStatus() == AppointmentStatus.SCHEDULED ? Kind.REQUEST_DECLINED
                : Kind.CANCELLED_BY_SALON;
        appointment.cancel(clock.instant(), cancelledBy, normalize(reason));
        AppointmentSummary summary = summarize(appointmentRepository.save(appointment));
        notifier.notifyClient(kind, summary);
        return summary;
    }

    @Transactional
    public AppointmentSummary complete(UUID salonId, UUID appointmentId) {
        schedulingLock.lock(salonId);
        Appointment appointment = requireInSalon(salonId, appointmentId);
        requireStatus(appointment, "terminer", AppointmentStatus.CONFIRMED);
        appointment.complete();
        return summarize(appointmentRepository.save(appointment));
    }

    @Transactional
    public AppointmentSummary markNoShow(UUID salonId, UUID appointmentId) {
        schedulingLock.lock(salonId);
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
