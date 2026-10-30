package com.apontaja.back.appointment.application;

import com.apontaja.back.appointment.application.ClientAppointmentNotification.Kind;
import com.apontaja.back.appointment.domain.Appointment;
import com.apontaja.back.appointment.domain.AppointmentRepository;
import com.apontaja.back.appointment.domain.AppointmentResourceRepository;
import com.apontaja.back.appointment.domain.AppointmentStatus;
import com.apontaja.back.appointment.domain.RequestExpiryPolicy;
import com.apontaja.back.shared.domain.SalonSchedulingLock;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Expire une demande, dans sa propre transaction. Prend le verrou de planification du salon puis relit la demande :
 * une confirmation ou un refus du staff, ou une annulation du client, déjà validés, l'emportent (rien n'est fait).
 * Bean distinct de {@link RequestExpiryService} pour que la transaction passe par le proxy.
 */
@Component
class RequestExpiryProcessor {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentResourceRepository appointmentResourceRepository;
    private final SalonSchedulingLock schedulingLock;
    private final StaffAppointmentNotifier notifier;

    RequestExpiryProcessor(AppointmentRepository appointmentRepository,
            AppointmentResourceRepository appointmentResourceRepository, SalonSchedulingLock schedulingLock,
            StaffAppointmentNotifier notifier) {
        this.appointmentRepository = appointmentRepository;
        this.appointmentResourceRepository = appointmentResourceRepository;
        this.schedulingLock = schedulingLock;
        this.notifier = notifier;
    }

    /** Vrai si la demande a été expirée. L'email part après la validation (aucun si le début est passé). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expire(UUID appointmentId, UUID salonId, Instant now) {
        schedulingLock.lock(salonId);
        Optional<Appointment> found = appointmentRepository.findAliveById(appointmentId)
                .filter(a -> a.getSalonId().equals(salonId));
        if (found.isEmpty()) {
            return false;
        }
        Appointment appointment = found.get();
        if (appointment.getStatus() != AppointmentStatus.SCHEDULED
                || !RequestExpiryPolicy.isDue(appointment.getCreatedAt(), appointment.getStartAt(), now)) {
            return false;
        }
        appointment.cancel(now, null, RequestExpiryPolicy.EXPIRY_REASON);
        Appointment saved = appointmentRepository.save(appointment);
        UUID resourceId = appointmentResourceRepository.findResourceIdsByAppointmentId(saved.getId()).stream()
                .findFirst().orElse(null);
        notifier.notifyClient(Kind.REQUEST_EXPIRED, AppointmentSummary.of(saved, resourceId));
        return true;
    }
}
