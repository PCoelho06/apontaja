package com.apontaja.back.resource.application;

import com.apontaja.back.resource.domain.Resource;
import com.apontaja.back.resource.domain.ResourceRepository;
import com.apontaja.back.resource.domain.Schedule;
import com.apontaja.back.resource.domain.ScheduleOwnerLock;
import com.apontaja.back.resource.domain.ScheduleRepository;
import com.apontaja.back.shared.domain.IdGenerator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Horaires récurrents d'un salon ou d'une ressource, par remplacement complet
 * de la semaine. Une liste vide efface : pour une ressource cela signifie "pas
 * d'horaire propre, suit ceux du salon".
 */
@Service
public class ScheduleManagementService {

    private static final Comparator<ScheduleSlotSummary> BY_DAY_THEN_START = Comparator
            .comparing(ScheduleSlotSummary::dayOfWeek).thenComparing(ScheduleSlotSummary::startTime);

    private final ScheduleRepository scheduleRepository;
    private final ResourceRepository resourceRepository;
    private final ScheduleOwnerLock ownerLock;
    private final IdGenerator idGenerator;
    private final Clock clock;

    ScheduleManagementService(ScheduleRepository scheduleRepository, ResourceRepository resourceRepository,
            ScheduleOwnerLock ownerLock, IdGenerator idGenerator, Clock clock) {
        this.scheduleRepository = scheduleRepository;
        this.resourceRepository = resourceRepository;
        this.ownerLock = ownerLock;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional
    public List<ScheduleSlotSummary> replaceSalonSchedule(UUID salonId, List<ScheduleSlotCommand> commands) {
        List<ScheduleSlotSummary> slots = parseAndValidate(commands);

        ownerLock.lock(salonId);
        scheduleRepository.deleteBySalonId(salonId);
        Instant now = clock.instant();
        saveIfAny(slots.stream().map(
                s -> Schedule.forSalon(idGenerator.generate(), salonId, s.dayOfWeek(), s.startTime(), s.endTime(), now))
                .toList());
        return slots;
    }

    @Transactional
    public List<ScheduleSlotSummary> replaceResourceSchedule(UUID salonId, UUID resourceId,
            List<ScheduleSlotCommand> commands) {
        List<ScheduleSlotSummary> slots = parseAndValidate(commands);
        requireResourceInSalon(salonId, resourceId);

        ownerLock.lock(resourceId);
        scheduleRepository.deleteByResourceId(resourceId);
        Instant now = clock.instant();
        saveIfAny(slots.stream().map(s -> Schedule.forResource(idGenerator.generate(), resourceId, s.dayOfWeek(),
                s.startTime(), s.endTime(), now)).toList());
        return slots;
    }

    @Transactional(readOnly = true)
    public List<ScheduleSlotSummary> getSalonSchedule(UUID salonId) {
        return toSortedSummaries(scheduleRepository.findBySalonId(salonId));
    }

    @Transactional(readOnly = true)
    public List<ScheduleSlotSummary> getResourceSchedule(UUID salonId, UUID resourceId) {
        requireResourceInSalon(salonId, resourceId);
        return toSortedSummaries(scheduleRepository.findByResourceId(resourceId));
    }

    private void saveIfAny(List<Schedule> schedules) {
        if (!schedules.isEmpty()) {
            scheduleRepository.saveAll(schedules);
        }
    }

    private void requireResourceInSalon(UUID salonId, UUID resourceId) {
        resourceRepository.findAliveById(resourceId).map(Resource::getSalonId).filter(salonId::equals)
                .orElseThrow(ResourceNotFoundException::new);
    }

    private static List<ScheduleSlotSummary> toSortedSummaries(List<Schedule> schedules) {
        return schedules.stream().map(s -> new ScheduleSlotSummary(s.getDayOfWeek(), s.getStartTime(), s.getEndTime()))
                .sorted(BY_DAY_THEN_START).toList();
    }

    private static List<ScheduleSlotSummary> parseAndValidate(List<ScheduleSlotCommand> commands) {
        Objects.requireNonNull(commands, "commands");
        List<ScheduleSlotSummary> slots = commands.stream().map(ScheduleManagementService::parse)
                .sorted(BY_DAY_THEN_START).toList();

        for (int i = 1; i < slots.size(); i++) {
            ScheduleSlotSummary previous = slots.get(i - 1);
            ScheduleSlotSummary current = slots.get(i);
            if (previous.dayOfWeek() == current.dayOfWeek() && current.startTime().isBefore(previous.endTime())) {
                throw new InvalidTimeRangeException("Deux plages se chevauchent le " + current.dayOfWeek() + ".");
            }
        }
        return slots;
    }

    private static ScheduleSlotSummary parse(ScheduleSlotCommand command) {
        DayOfWeek day = parseDay(command.dayOfWeek());
        LocalTime start = parseTime(command.startTime());
        LocalTime end = parseTime(command.endTime());
        if (!end.isAfter(start)) {
            throw new InvalidTimeRangeException("La fin doit être postérieure au début (" + day + ").");
        }
        return new ScheduleSlotSummary(day, start, end);
    }

    private static DayOfWeek parseDay(String value) {
        try {
            return DayOfWeek.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidTimeRangeException("Jour invalide : " + value);
        }
    }

    private static LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value.trim());
        } catch (DateTimeParseException | NullPointerException e) {
            throw new InvalidTimeRangeException("Heure invalide (attendu HH:mm) : " + value);
        }
    }
}
