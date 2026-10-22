package com.apontaja.back.appointment.application;

import com.apontaja.back.appointment.domain.Appointment;
import com.apontaja.back.appointment.domain.AppointmentRepository;
import com.apontaja.back.appointment.domain.AppointmentResource;
import com.apontaja.back.appointment.domain.AppointmentResourceRepository;
import com.apontaja.back.appointment.domain.AppointmentStatus;
import com.apontaja.back.appointment.domain.BookingQuotaRepository;
import com.apontaja.back.appointment.domain.BusyInterval;
import com.apontaja.back.appointment.domain.ResourceAssigner;
import com.apontaja.back.appointment.domain.ResourceBusyRepository;
import com.apontaja.back.customer.application.CustomerSelfService;
import com.apontaja.back.resource.application.InvalidSalonTimezoneException;
import com.apontaja.back.resource.application.ResourceQueryService;
import com.apontaja.back.resource.application.ResourceSummary;
import com.apontaja.back.service.application.ResourceTerms;
import com.apontaja.back.service.application.ServiceOffering;
import com.apontaja.back.service.application.ServiceQueryService;
import com.apontaja.back.shared.domain.IdGenerator;
import com.apontaja.back.shared.domain.SalonSchedulingLock;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Réservation en ligne par un compte connecté (email vérifié). Sous le verrou du salon : profil du compte dans le
 * salon, quota de RDV à venir, puis le créneau est recalculé par {@link PublicBookingService#slots} (une seule
 * définition d'un créneau libre) ; le début demandé doit en faire partie. En « sans préférence », la ressource est
 * choisie par {@link ResourceAssigner}. Statut initial SCHEDULED, ou CONFIRMED si le salon confirme
 * automatiquement. Salon non réservable, prestation ou ressource inconnue : même 404 uniforme que la lecture
 * publique. La contrainte d'exclusion PostgreSQL reste le dernier filet (409).
 */
@Service
public class OnlineBookingService {

    /** RDV SCHEDULED/CONFIRMED à venir au maximum, par client et par salon. */
    static final int MAX_UPCOMING_PER_CUSTOMER = 5;

    private final PublicBookingService publicBookingService;
    private final ServiceQueryService serviceQueryService;
    private final ResourceQueryService resourceQueryService;
    private final ResourceBusyRepository resourceBusyRepository;
    private final CustomerSelfService customerSelfService;
    private final BookingQuotaRepository bookingQuotaRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentResourceRepository appointmentResourceRepository;
    private final SalonSchedulingLock schedulingLock;
    private final IdGenerator idGenerator;
    private final Clock clock;

    OnlineBookingService(PublicBookingService publicBookingService, ServiceQueryService serviceQueryService,
            ResourceQueryService resourceQueryService, ResourceBusyRepository resourceBusyRepository,
            CustomerSelfService customerSelfService, BookingQuotaRepository bookingQuotaRepository,
            AppointmentRepository appointmentRepository, AppointmentResourceRepository appointmentResourceRepository,
            SalonSchedulingLock schedulingLock, IdGenerator idGenerator, Clock clock) {
        this.publicBookingService = publicBookingService;
        this.serviceQueryService = serviceQueryService;
        this.resourceQueryService = resourceQueryService;
        this.resourceBusyRepository = resourceBusyRepository;
        this.customerSelfService = customerSelfService;
        this.bookingQuotaRepository = bookingQuotaRepository;
        this.appointmentRepository = appointmentRepository;
        this.appointmentResourceRepository = appointmentResourceRepository;
        this.schedulingLock = schedulingLock;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional
    public OnlineBookingResult book(OnlineBookingCommand command) {
        Instant startAt = AppointmentCreationService.parseStartAt(command.startAt());
        UUID salonId = command.salonId();

        PublicBooking.SalonInfo salon = publicBookingService.salon(salonId);
        ZoneId zone = parseZone(salon.timezone());
        ServiceOffering offering = serviceQueryService.findOffering(salonId, command.serviceId())
                .orElseThrow(PublicBookingNotFoundException::new);
        List<ResourceTerms> candidates = offering.resources().stream()
                .filter(terms -> command.resourceId() == null || terms.resourceId().equals(command.resourceId()))
                .toList();
        if (candidates.isEmpty()) {
            throw new PublicBookingNotFoundException();
        }

        schedulingLock.lock(salonId);

        UUID profileId = customerSelfService.resolveProfileForBooking(command.accountId(), salonId, command.phone(),
                command.shareContactConsent());
        Instant now = clock.instant();
        if (bookingQuotaRepository.countUpcomingActive(salonId, profileId, now) >= MAX_UPCOMING_PER_CUSTOMER) {
            throw new BookingQuotaExceededException(MAX_UPCOMING_PER_CUSTOMER);
        }

        String date = startAt.atZone(zone).toLocalDate().toString();
        PublicBooking.Slots slots = publicBookingService.slots(salonId, command.serviceId(), command.resourceId(),
                date, date);
        List<UUID> free = slots.days().stream().flatMap(day -> day.slots().stream())
                .filter(slot -> slot.startAt().equals(startAt)).findFirst().map(PublicBooking.Slot::resourceIds)
                .orElseThrow(SlotUnavailableException::new);

        UUID chosen = free.size() == 1 ? free.get(0) : assign(salonId, free, startAt, zone);
        ResourceTerms terms = candidates.stream().filter(t -> t.resourceId().equals(chosen)).findFirst()
                .orElseThrow(SlotUnavailableException::new);

        Instant endAt = startAt.plus(terms.durationMinutes(), ChronoUnit.MINUTES);
        AppointmentStatus initial = salon.autoConfirm() ? AppointmentStatus.CONFIRMED : AppointmentStatus.SCHEDULED;
        Appointment appointment = appointmentRepository.save(new Appointment(idGenerator.generate(), salonId,
                profileId, command.serviceId(), startAt, endAt, terms.priceCents(), terms.durationMinutes(), now,
                initial));
        try {
            appointmentResourceRepository.save(new AppointmentResource(appointment.getId(), chosen));
        } catch (DataIntegrityViolationException e) {
            throw new AppointmentConflictException();
        }

        String resourceName = slots.resources().stream().filter(r -> r.resourceId().equals(chosen))
                .map(PublicBooking.Resource::name).findFirst().orElse(null);
        return new OnlineBookingResult(appointment.getId(), salonId, command.serviceId(), offering.service().name(),
                chosen, resourceName, appointment.getStartAt(), appointment.getEndAt(),
                appointment.getStatus().name(), appointment.getPriceAtBookingCents(),
                appointment.getDurationAtBookingMinutes(), appointment.getCreatedAt());
    }

    private UUID assign(UUID salonId, List<UUID> free, Instant startAt, ZoneId zone) {
        Map<UUID, Instant> createdAt = resourceQueryService.findAliveBySalonId(salonId).stream()
                .collect(Collectors.toMap(ResourceSummary::resourceId, ResourceSummary::createdAt, (a, b) -> a));
        LocalDate day = startAt.atZone(zone).toLocalDate();
        Instant dayStart = day.atStartOfDay(zone).toInstant();
        Instant dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant();
        List<BusyInterval> busy = resourceBusyRepository.findActiveIntervals(free, dayStart, dayEnd);
        Function<UUID, ResourceAssigner.Candidate> toCandidate = id -> new ResourceAssigner.Candidate(id,
                createdAt.getOrDefault(id, Instant.EPOCH));
        return ResourceAssigner.choose(free.stream().map(toCandidate).toList(), busy, dayStart, dayEnd);
    }

    private static ZoneId parseZone(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException | NullPointerException e) {
            throw new InvalidSalonTimezoneException();
        }
    }
}
