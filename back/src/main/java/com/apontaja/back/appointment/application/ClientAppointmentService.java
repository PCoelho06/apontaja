package com.apontaja.back.appointment.application;

import com.apontaja.back.appointment.domain.Appointment;
import com.apontaja.back.appointment.domain.AppointmentRepository;
import com.apontaja.back.appointment.domain.AppointmentStatus;
import com.apontaja.back.appointment.domain.CancellationPolicy;
import com.apontaja.back.appointment.domain.ClientAppointmentRepository;
import com.apontaja.back.appointment.domain.ClientAppointmentRow;
import com.apontaja.back.appointment.domain.ClientAppointmentRows;
import com.apontaja.back.salon.application.BookingSettingsService;
import com.apontaja.back.shared.domain.SalonSchedulingLock;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * « Mes rendez-vous » et annulation par le client. Tout est scopé par compte : un RDV qui n'est pas au compte est
 * introuvable (404 uniforme). Le délai d'annulation est le réglage courant du salon, même si la réservation en
 * ligne a été désactivée depuis. L'annulation prend le verrou de planification du salon (comme le déplacement par
 * le staff) et prévient le client par email après la validation.
 */
@Service
public class ClientAppointmentService {

    static final String CANCEL_REASON = "Annulé par le client";
    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;

    private final ClientAppointmentRepository clientAppointmentRepository;
    private final AppointmentRepository appointmentRepository;
    private final BookingSettingsService bookingSettingsService;
    private final SalonSchedulingLock schedulingLock;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ClientAppointmentService(ClientAppointmentRepository clientAppointmentRepository,
            AppointmentRepository appointmentRepository, BookingSettingsService bookingSettingsService,
            SalonSchedulingLock schedulingLock, ApplicationEventPublisher events, Clock clock) {
        this.clientAppointmentRepository = clientAppointmentRepository;
        this.appointmentRepository = appointmentRepository;
        this.bookingSettingsService = bookingSettingsService;
        this.schedulingLock = schedulingLock;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ClientAppointmentPage list(UUID accountId, ClientAppointmentScope scope, Integer page, Integer size) {
        int pageIndex = page == null ? 0 : Math.max(page, 0);
        int pageSize = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        Instant now = clock.instant();

        ClientAppointmentRows result = clientAppointmentRepository.findForAccount(accountId,
                scope == ClientAppointmentScope.UPCOMING, now, pageIndex, pageSize);
        Map<UUID, Integer> deadlines = new HashMap<>();
        List<ClientAppointment> content = result.rows().stream()
                .map(row -> toView(row, deadlines.computeIfAbsent(row.salonId(), this::deadlineHours), now))
                .toList();
        int totalPages = (int) Math.ceil(result.totalElements() / (double) pageSize);
        return new ClientAppointmentPage(content, pageIndex, pageSize, result.totalElements(), totalPages);
    }

    @Transactional
    public ClientAppointment cancel(UUID accountId, UUID appointmentId) {
        ClientAppointmentRow owned = clientAppointmentRepository.findOwned(accountId, appointmentId)
                .orElseThrow(ClientAppointmentNotFoundException::new);

        // L'état est relu sous le verrou : un déplacement ou une annulation concurrents sont déjà validés.
        schedulingLock.lock(owned.salonId());
        Appointment appointment = appointmentRepository.findAliveById(appointmentId)
                .orElseThrow(ClientAppointmentNotFoundException::new);
        Instant now = clock.instant();
        int deadline = deadlineHours(owned.salonId());
        CancellationPolicy.Verdict verdict = CancellationPolicy.evaluate(appointment.getStatus(),
                appointment.getStartAt(), deadline, now);
        if (verdict == CancellationPolicy.Verdict.NOT_ALLOWED) {
            throw new AppointmentNotCancellableException();
        }
        if (verdict == CancellationPolicy.Verdict.DEADLINE_PASSED) {
            throw new CancellationDeadlinePassedException(deadline);
        }

        appointment.cancel(now, accountId, CANCEL_REASON);
        appointmentRepository.save(appointment);

        ClientAppointmentRow updated = clientAppointmentRepository.findOwned(accountId, appointmentId)
                .orElseThrow(() -> new IllegalStateException("Rendez-vous " + appointmentId + " introuvable."));
        events.publishEvent(new ClientAppointmentNotification(ClientAppointmentNotification.Kind.CANCELLED,
                accountId, appointmentId, updated.salonName(), updated.salonAddress(), updated.salonPostalCode(),
                updated.salonCity(), updated.salonTimezone(), updated.serviceName(), updated.resourceName(),
                updated.startAt(), updated.endAt(), updated.priceCents(), updated.durationMinutes(), deadline));
        return toView(updated, deadline, now);
    }

    private int deadlineHours(UUID salonId) {
        return bookingSettingsService.get(salonId).cancellationDeadlineHours();
    }

    private static ClientAppointment toView(ClientAppointmentRow row, int deadlineHours, Instant now) {
        AppointmentStatus status = AppointmentStatus.valueOf(row.status());
        boolean cancellable = CancellationPolicy.evaluate(status, row.startAt(), deadlineHours,
                now) == CancellationPolicy.Verdict.ALLOWED;
        Instant until = CancellationPolicy.cancellableUntil(status, row.startAt(), deadlineHours);
        return new ClientAppointment(row.appointmentId(),
                new ClientAppointment.SalonRef(row.salonId(), row.salonName(), row.salonCity(),
                        row.salonTimezone()),
                new ClientAppointment.ServiceRef(row.serviceId(), row.serviceName()),
                new ClientAppointment.ResourceRef(row.resourceId(), row.resourceName()), row.startAt(),
                row.endAt(), row.status(), row.priceCents(), row.durationMinutes(), cancellable, until,
                row.cancelledAt(), row.expired());
    }
}
