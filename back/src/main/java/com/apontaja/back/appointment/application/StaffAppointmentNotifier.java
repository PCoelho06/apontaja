package com.apontaja.back.appointment.application;

import com.apontaja.back.appointment.application.ClientAppointmentNotification.Kind;
import com.apontaja.back.resource.application.ResourceQueryService;
import com.apontaja.back.resource.application.ResourceSummary;
import com.apontaja.back.salon.application.BookingSettingsService;
import com.apontaja.back.salon.application.SalonQueryService;
import com.apontaja.back.salon.application.SalonSummary;
import com.apontaja.back.service.application.ServiceQueryService;
import com.apontaja.back.service.application.ServiceSummary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Optional;

/**
 * Prépare et publie l'événement d'email au client pour une décision du staff (confirmation, refus d'une demande,
 * annulation par le salon). À appeler dans la transaction de la transition : l'email part après la validation
 * ({@link ClientAppointmentEmailListener}). Aucun email pour un rendez-vous déjà commencé ou passé. Toute erreur de
 * préparation est journalisée sans faire échouer la transition.
 */
@Component
class StaffAppointmentNotifier {

    private static final Logger log = LoggerFactory.getLogger(StaffAppointmentNotifier.class);

    private final SalonQueryService salonQueryService;
    private final ServiceQueryService serviceQueryService;
    private final ResourceQueryService resourceQueryService;
    private final BookingSettingsService bookingSettingsService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    StaffAppointmentNotifier(SalonQueryService salonQueryService, ServiceQueryService serviceQueryService,
            ResourceQueryService resourceQueryService, BookingSettingsService bookingSettingsService,
            ApplicationEventPublisher eventPublisher, Clock clock) {
        this.salonQueryService = salonQueryService;
        this.serviceQueryService = serviceQueryService;
        this.resourceQueryService = resourceQueryService;
        this.bookingSettingsService = bookingSettingsService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    void notifyClient(Kind kind, AppointmentSummary appointment) {
        try {
            if (!appointment.startAt().isAfter(clock.instant())) {
                return;
            }
            Optional<SalonSummary> salon = salonQueryService.findAliveById(appointment.salonId());
            if (salon.isEmpty()) {
                log.warn("Email {} non préparé : salon introuvable (rendez-vous {}).", kind,
                        appointment.appointmentId());
                return;
            }
            String serviceName = serviceQueryService.findAliveInSalon(appointment.salonId(), appointment.serviceId())
                    .map(ServiceSummary::name).orElse("-");
            String resourceName = resourceQueryService
                    .findAliveInSalon(appointment.salonId(), appointment.resourceId()).map(ResourceSummary::name)
                    .orElse(null);
            int deadlineHours = bookingSettingsService.get(appointment.salonId()).cancellationDeadlineHours();
            SalonSummary s = salon.get();
            eventPublisher.publishEvent(new ClientAppointmentNotification(kind, null, appointment.appointmentId(),
                    s.name(), s.address(), s.postalCode(), s.city(), s.timezone(), serviceName, resourceName,
                    appointment.startAt(), appointment.endAt(), appointment.priceAtBookingCents(),
                    appointment.durationAtBookingMinutes(), deadlineHours, appointment.customerProfileId()));
        } catch (RuntimeException e) {
            log.warn("Email {} non préparé (rendez-vous {}) : {}", kind, appointment.appointmentId(),
                    e.getClass().getName());
            log.debug("Détail de l'échec de préparation", e);
        }
    }
}
