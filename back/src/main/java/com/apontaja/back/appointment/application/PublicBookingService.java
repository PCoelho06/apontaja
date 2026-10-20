package com.apontaja.back.appointment.application;

import com.apontaja.back.appointment.domain.BusyInterval;
import com.apontaja.back.appointment.domain.ResourceBusyRepository;
import com.apontaja.back.appointment.domain.SlotGrid;
import com.apontaja.back.resource.application.AvailabilityPlan;
import com.apontaja.back.resource.application.AvailabilityService;
import com.apontaja.back.resource.application.InvalidSalonTimezoneException;
import com.apontaja.back.resource.application.InvalidTimeRangeException;
import com.apontaja.back.resource.application.ResourceQueryService;
import com.apontaja.back.resource.application.ResourceSummary;
import com.apontaja.back.salon.application.BookingSettings;
import com.apontaja.back.salon.application.BookingSettingsService;
import com.apontaja.back.salon.application.SalonQueryService;
import com.apontaja.back.salon.application.SalonSummary;
import com.apontaja.back.service.application.ResourceTerms;
import com.apontaja.back.service.application.ServiceOffering;
import com.apontaja.back.service.application.ServiceQueryService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lecture publique (sans authentification) de la réservation en ligne : fiche du salon, prestations
 * réservables, créneaux libres. Tout salon inconnu, supprimé ou sans réservation en ligne donne la même
 * {@link PublicBookingNotFoundException}.
 *
 * <p>
 * Un créneau est libre pour une ressource si, avec la durée effective de la prestation pour cette
 * ressource : il respecte horaires et fermetures ({@link AvailabilityPlan}), ne recouvre aucun RDV
 * actif de la ressource, commence après « maintenant + délai minimum » et avant « maintenant +
 * horizon ». « Sans préférence » = toutes les ressources associées à la prestation ; chaque début n'est
 * renvoyé qu'une fois avec les ressources qui peuvent le prendre.
 */
@Service
public class PublicBookingService {

    /** Nombre maximal de jours (inclus) demandés en une fois. */
    static final int MAX_RANGE_DAYS = 31;

    private final BookingSettingsService bookingSettingsService;
    private final SalonQueryService salonQueryService;
    private final ServiceQueryService serviceQueryService;
    private final ResourceQueryService resourceQueryService;
    private final AvailabilityService availabilityService;
    private final ResourceBusyRepository resourceBusyRepository;
    private final Clock clock;

    PublicBookingService(BookingSettingsService bookingSettingsService, SalonQueryService salonQueryService,
            ServiceQueryService serviceQueryService, ResourceQueryService resourceQueryService,
            AvailabilityService availabilityService, ResourceBusyRepository resourceBusyRepository, Clock clock) {
        this.bookingSettingsService = bookingSettingsService;
        this.salonQueryService = salonQueryService;
        this.serviceQueryService = serviceQueryService;
        this.resourceQueryService = resourceQueryService;
        this.availabilityService = availabilityService;
        this.resourceBusyRepository = resourceBusyRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PublicBooking.SalonInfo salon(UUID salonId) {
        Bookable bookable = requireBookable(salonId);
        SalonSummary salon = bookable.salon();
        BookingSettings settings = bookable.settings();
        return new PublicBooking.SalonInfo(salon.salonId(), salon.name(), salon.address(), salon.postalCode(),
                salon.city(), salon.country(), salon.phone(), salon.timezone(), settings.autoConfirm(),
                settings.minNoticeMinutes(), settings.horizonDays(), settings.cancellationDeadlineHours());
    }

    /** Prestations ayant au moins une ressource vivante associée. */
    @Transactional(readOnly = true)
    public List<PublicBooking.Offering> services(UUID salonId) {
        requireBookable(salonId);
        Map<UUID, String> names = resourceNames(salonId);
        return serviceQueryService.findOfferingsBySalonId(salonId).stream()
                .filter(offering -> !offering.resources().isEmpty())
                .map(offering -> new PublicBooking.Offering(offering.service().serviceId(),
                        offering.service().name(), offering.service().description(),
                        offering.service().defaultDurationMinutes(), offering.service().defaultPriceCents(),
                        toResources(offering.resources(), names)))
                .toList();
    }

    @Transactional(readOnly = true)
    public PublicBooking.Slots slots(UUID salonId, UUID serviceId, UUID resourceId, String from, String to) {
        Bookable bookable = requireBookable(salonId);
        BookingSettings settings = bookable.settings();
        ZoneId zone = parseZone(bookable.salon().timezone());
        LocalDate firstDate = parseDate(from, "from");
        LocalDate lastDate = parseDate(to, "to");
        if (lastDate.isBefore(firstDate)) {
            throw new InvalidTimeRangeException("Le paramètre to doit être postérieur ou égal à from.");
        }
        if (ChronoUnit.DAYS.between(firstDate, lastDate) >= MAX_RANGE_DAYS) {
            throw new InvalidTimeRangeException("La période demandée ne peut pas dépasser " + MAX_RANGE_DAYS
                    + " jours.");
        }

        ServiceOffering offering = serviceQueryService.findOffering(salonId, serviceId)
                .orElseThrow(PublicBookingNotFoundException::new);
        List<ResourceTerms> candidates = offering.resources().stream()
                .filter(terms -> resourceId == null || terms.resourceId().equals(resourceId)).toList();
        if (candidates.isEmpty()) {
            throw new PublicBookingNotFoundException();
        }
        List<PublicBooking.Resource> resources = toResources(candidates, resourceNames(salonId));

        Instant now = clock.instant();
        List<Instant> starts = SlotGrid.candidateStarts(zone, firstDate, lastDate,
                settings.slotGranularityMinutes(), now.plus(settings.minNoticeMinutes(), ChronoUnit.MINUTES),
                now.plus(settings.horizonDays(), ChronoUnit.DAYS));
        if (starts.isEmpty()) {
            return new PublicBooking.Slots(serviceId, zone.getId(), resources, List.of());
        }

        Instant windowStart = firstDate.atStartOfDay(zone).toInstant();
        Instant windowEnd = lastDate.plusDays(1).atStartOfDay(zone).toInstant();
        int maxDuration = candidates.stream().mapToInt(ResourceTerms::durationMinutes).max().orElse(0);
        Instant planEnd = windowEnd.plus(maxDuration, ChronoUnit.MINUTES);

        Map<UUID, List<BusyInterval>> busyByResource = resourceBusyRepository
                .findActiveIntervals(candidates.stream().map(ResourceTerms::resourceId).toList(), windowStart,
                        planEnd)
                .stream().collect(Collectors.groupingBy(BusyInterval::resourceId));

        SortedMap<Instant, List<UUID>> free = new TreeMap<>();
        for (ResourceTerms terms : candidates) {
            AvailabilityPlan plan = availabilityService.plan(salonId, terms.resourceId(), windowStart, planEnd);
            List<BusyInterval> busy = busyByResource.getOrDefault(terms.resourceId(), List.of());
            for (Instant start : starts) {
                Instant end = start.plus(terms.durationMinutes(), ChronoUnit.MINUTES);
                if (SlotGrid.isFree(start, end, busy) && plan.isAvailable(start, end)) {
                    free.computeIfAbsent(start, k -> new ArrayList<>()).add(terms.resourceId());
                }
            }
        }
        return new PublicBooking.Slots(serviceId, zone.getId(), resources, groupByDay(free, zone));
    }

    private static List<PublicBooking.Day> groupByDay(SortedMap<Instant, List<UUID>> free, ZoneId zone) {
        Map<LocalDate, List<PublicBooking.Slot>> byDate = new LinkedHashMap<>();
        free.forEach((start, resourceIds) -> byDate.computeIfAbsent(start.atZone(zone).toLocalDate(),
                k -> new ArrayList<>()).add(new PublicBooking.Slot(start, List.copyOf(resourceIds))));
        return byDate.entrySet().stream()
                .map(entry -> new PublicBooking.Day(entry.getKey().toString(), entry.getValue())).toList();
    }

    private Bookable requireBookable(UUID salonId) {
        SalonSummary salon = salonQueryService.findAliveById(salonId).orElseThrow(PublicBookingNotFoundException::new);
        BookingSettings settings = bookingSettingsService.get(salonId);
        if (!settings.onlineBookingEnabled()) {
            throw new PublicBookingNotFoundException();
        }
        return new Bookable(salon, settings);
    }

    private Map<UUID, String> resourceNames(UUID salonId) {
        return resourceQueryService.findAliveBySalonId(salonId).stream()
                .collect(Collectors.toMap(ResourceSummary::resourceId, ResourceSummary::name, (a, b) -> a));
    }

    private static List<PublicBooking.Resource> toResources(List<ResourceTerms> terms, Map<UUID, String> names) {
        return terms.stream().map(t -> new PublicBooking.Resource(t.resourceId(), names.get(t.resourceId()),
                t.priceCents(), t.durationMinutes())).toList();
    }

    private static ZoneId parseZone(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException | NullPointerException e) {
            throw new InvalidSalonTimezoneException();
        }
    }

    private static LocalDate parseDate(String value, String name) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException | NullPointerException e) {
            throw new InvalidTimeRangeException("Le paramètre " + name + " est obligatoire (format AAAA-MM-JJ).");
        }
    }

    private record Bookable(SalonSummary salon, BookingSettings settings) {
    }
}
