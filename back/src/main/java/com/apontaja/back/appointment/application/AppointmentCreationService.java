package com.apontaja.back.appointment.application;

import com.apontaja.back.appointment.domain.Appointment;
import com.apontaja.back.appointment.domain.AppointmentRepository;
import com.apontaja.back.appointment.domain.AppointmentResource;
import com.apontaja.back.appointment.domain.AppointmentResourceRepository;
import com.apontaja.back.customer.application.CustomerQueryService;
import com.apontaja.back.resource.application.AvailabilityCheck;
import com.apontaja.back.resource.application.AvailabilityService;
import com.apontaja.back.resource.application.InvalidTimeRangeException;
import com.apontaja.back.resource.application.ResourceNotFoundException;
import com.apontaja.back.resource.application.ResourceQueryService;
import com.apontaja.back.service.application.ServiceNotFoundException;
import com.apontaja.back.service.application.ServiceQueryService;
import com.apontaja.back.service.application.ServiceTerms;
import com.apontaja.back.shared.domain.IdGenerator;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * Réservation d'un RDV (v1 : une seule ressource). L'ordre des vérifications
 * est délibéré : références (404), association service/ressource (400),
 * disponibilité horaires/fermetures (409), puis persistance — la contrainte
 * d'exclusion PostgreSQL reste la dernière ligne de défense contre une
 * réservation concurrente passée entre deux vérifications applicatives
 * (SQLSTATE 23P01, mappé ici en AppointmentConflictException).
 */
@Service
public class AppointmentCreationService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentResourceRepository appointmentResourceRepository;
    private final ResourceQueryService resourceQueryService;
    private final ServiceQueryService serviceQueryService;
    private final CustomerQueryService customerQueryService;
    private final AvailabilityService availabilityService;
    private final IdGenerator idGenerator;
    private final Clock clock;

    AppointmentCreationService(AppointmentRepository appointmentRepository,
            AppointmentResourceRepository appointmentResourceRepository, ResourceQueryService resourceQueryService,
            ServiceQueryService serviceQueryService, CustomerQueryService customerQueryService,
            AvailabilityService availabilityService, IdGenerator idGenerator, Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.appointmentResourceRepository = appointmentResourceRepository;
        this.resourceQueryService = resourceQueryService;
        this.serviceQueryService = serviceQueryService;
        this.customerQueryService = customerQueryService;
        this.availabilityService = availabilityService;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional
    public AppointmentSummary create(CreateAppointmentCommand command) {
        Instant startAt = parseStartAt(command.startAt());

        resourceQueryService.findAliveInSalon(command.salonId(), command.resourceId())
                .orElseThrow(ResourceNotFoundException::new);
        serviceQueryService.findAliveInSalon(command.salonId(), command.serviceId())
                .orElseThrow(ServiceNotFoundException::new);
        customerQueryService.findAliveInSalon(command.salonId(), command.customerProfileId())
                .orElseThrow(CustomerNotFoundException::new);

        ServiceTerms terms = serviceQueryService
                .resolveTerms(command.salonId(), command.serviceId(), command.resourceId())
                .orElseThrow(ServiceResourceNotAssociatedException::new);

        Instant endAt = startAt.plus(terms.durationMinutes(), ChronoUnit.MINUTES);

        AvailabilityCheck availability = availabilityService.check(command.salonId(), command.resourceId(), startAt,
                endAt);
        if (!availability.available()) {
            throw new AppointmentNotAvailableException(availability.issues());
        }

        Instant now = clock.instant();
        Appointment appointment = new Appointment(idGenerator.generate(), command.salonId(),
                command.customerProfileId(), command.serviceId(), startAt, endAt, terms.priceCents(),
                terms.durationMinutes(), now);
        appointment = appointmentRepository.save(appointment);

        AppointmentResource link = new AppointmentResource(appointment.getId(), command.resourceId());
        try {
            appointmentResourceRepository.save(link);
        } catch (DataIntegrityViolationException e) {
            throw new AppointmentConflictException();
        }

        return AppointmentSummary.of(appointment, command.resourceId());
    }

    static Instant parseStartAt(String value) {
        try {
            return OffsetDateTime.parse(value.trim()).toInstant();
        } catch (DateTimeParseException | NullPointerException e) {
            throw new InvalidTimeRangeException(
                    "Date/heure de début invalide (attendu ISO 8601 avec offset) : " + value);
        }
    }
}
