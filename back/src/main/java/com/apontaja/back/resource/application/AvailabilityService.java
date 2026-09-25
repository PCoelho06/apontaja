package com.apontaja.back.resource.application;

import com.apontaja.back.resource.domain.AvailabilityEngine;
import com.apontaja.back.resource.domain.AvailabilityInput;
import com.apontaja.back.resource.domain.AvailabilityResult;
import com.apontaja.back.resource.domain.Closure;
import com.apontaja.back.resource.domain.ClosureRepository;
import com.apontaja.back.resource.domain.Resource;
import com.apontaja.back.resource.domain.ResourceRepository;
import com.apontaja.back.resource.domain.Schedule;
import com.apontaja.back.resource.domain.ScheduleRepository;
import com.apontaja.back.resource.domain.TimeInterval;
import com.apontaja.back.resource.domain.WeeklyWindow;
import com.apontaja.back.salon.application.SalonQueryService;
import com.apontaja.back.salon.application.SalonSummary;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Point d'entrée des autres domaines (appointment) pour savoir si un créneau
 * est réservable sur une ressource : charge les horaires et fermetures utiles
 * puis délègue au moteur pur {@link AvailabilityEngine}. Ne tient pas compte
 * des rendez-vous existants (rôle du domaine appointment).
 */
@Service
public class AvailabilityService {

    private final ScheduleRepository scheduleRepository;
    private final ClosureRepository closureRepository;
    private final ResourceRepository resourceRepository;
    private final SalonQueryService salonQueryService;

    AvailabilityService(ScheduleRepository scheduleRepository, ClosureRepository closureRepository,
            ResourceRepository resourceRepository, SalonQueryService salonQueryService) {
        this.scheduleRepository = scheduleRepository;
        this.closureRepository = closureRepository;
        this.resourceRepository = resourceRepository;
        this.salonQueryService = salonQueryService;
    }

    @Transactional(readOnly = true)
    public AvailabilityCheck check(UUID salonId, UUID resourceId, Instant start, Instant end) {
        if (!end.isAfter(start)) {
            throw new InvalidTimeRangeException("La fin doit être postérieure au début.");
        }
        SalonSummary salon = salonQueryService.findAliveById(salonId).orElseThrow(ResourceNotFoundException::new);
        resourceRepository.findAliveById(resourceId).map(Resource::getSalonId).filter(salonId::equals)
                .orElseThrow(ResourceNotFoundException::new);
        ZoneId zone = parseZone(salon.timezone());

        AvailabilityInput input = new AvailabilityInput(new TimeInterval(start, end), zone,
                windows(scheduleRepository.findBySalonId(salonId)),
                windows(scheduleRepository.findByResourceId(resourceId)),
                intervals(closureRepository.findOverlappingBySalonId(salonId, start, end)),
                intervals(closureRepository.findOverlappingByResourceId(resourceId, start, end)));

        AvailabilityResult result = AvailabilityEngine.check(input);
        return new AvailabilityCheck(result.isAvailable(),
                result.violations().stream().map(v -> AvailabilityIssue.valueOf(v.name())).toList());
    }

    private static ZoneId parseZone(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException | NullPointerException e) {
            throw new InvalidSalonTimezoneException();
        }
    }

    private static List<WeeklyWindow> windows(List<Schedule> schedules) {
        return schedules.stream().map(WeeklyWindow::from).toList();
    }

    private static List<TimeInterval> intervals(List<Closure> closures) {
        return closures.stream().map(c -> new TimeInterval(c.getStartAt(), c.getEndAt())).toList();
    }
}
