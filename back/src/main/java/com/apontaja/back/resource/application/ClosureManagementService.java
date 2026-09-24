package com.apontaja.back.resource.application;

import com.apontaja.back.resource.domain.Closure;
import com.apontaja.back.resource.domain.ClosureRepository;
import com.apontaja.back.resource.domain.Resource;
import com.apontaja.back.resource.domain.ResourceRepository;
import com.apontaja.back.shared.domain.IdGenerator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/**
 * Fermetures d'un salon (paramètre {@code resourceId} null) ou d'une ressource
 * de ce salon. Le périmètre est toujours revérifié : une fermeture n'est
 * accessible que via son propre salon / sa propre ressource.
 */
@Service
public class ClosureManagementService {

    private static final Instant MIN_BOUND = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant MAX_BOUND = Instant.parse("9999-12-31T23:59:59Z");

    private final ClosureRepository closureRepository;
    private final ResourceRepository resourceRepository;
    private final IdGenerator idGenerator;
    private final Clock clock;

    ClosureManagementService(ClosureRepository closureRepository, ResourceRepository resourceRepository,
            IdGenerator idGenerator, Clock clock) {
        this.closureRepository = closureRepository;
        this.resourceRepository = resourceRepository;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional
    public ClosureSummary create(UUID salonId, UUID resourceId, ClosureCommand command) {
        Instant start = parseInstant(command.startAt());
        Instant end = parseInstant(command.endAt());
        requireValidPeriod(start, end);
        requireScope(salonId, resourceId);

        String reason = normalizeReason(command.reason());
        Instant now = clock.instant();
        Closure closure = resourceId == null
                ? Closure.forSalon(idGenerator.generate(), salonId, start, end, reason, now)
                : Closure.forResource(idGenerator.generate(), resourceId, start, end, reason, now);
        return ClosureSummary.from(closureRepository.save(closure));
    }

    @Transactional
    public ClosureSummary update(UUID salonId, UUID resourceId, UUID closureId, ClosureCommand command) {
        Instant start = parseInstant(command.startAt());
        Instant end = parseInstant(command.endAt());
        requireValidPeriod(start, end);
        Closure closure = requireClosure(salonId, resourceId, closureId);

        closure.reschedule(start, end, normalizeReason(command.reason()));
        return ClosureSummary.from(closureRepository.save(closure));
    }

    @Transactional
    public void delete(UUID salonId, UUID resourceId, UUID closureId) {
        closureRepository.delete(requireClosure(salonId, resourceId, closureId));
    }

    /** Fermetures recouvrant [from, to) ; bornes absentes = sans limite. */
    @Transactional(readOnly = true)
    public List<ClosureSummary> list(UUID salonId, UUID resourceId, String from, String to) {
        Instant fromInstant = from == null ? MIN_BOUND : parseInstant(from);
        Instant toInstant = to == null ? MAX_BOUND : parseInstant(to);
        requireValidPeriod(fromInstant, toInstant);
        requireScope(salonId, resourceId);

        List<Closure> closures = resourceId == null
                ? closureRepository.findOverlappingBySalonId(salonId, fromInstant, toInstant)
                : closureRepository.findOverlappingByResourceId(resourceId, fromInstant, toInstant);
        return closures.stream().map(ClosureSummary::from).toList();
    }

    private void requireScope(UUID salonId, UUID resourceId) {
        if (resourceId != null) {
            resourceRepository.findAliveById(resourceId).map(Resource::getSalonId).filter(salonId::equals)
                    .orElseThrow(ResourceNotFoundException::new);
        }
    }

    private Closure requireClosure(UUID salonId, UUID resourceId, UUID closureId) {
        requireScope(salonId, resourceId);
        return closureRepository.findById(closureId).filter(c -> inScope(c, salonId, resourceId))
                .orElseThrow(ClosureNotFoundException::new);
    }

    private static boolean inScope(Closure closure, UUID salonId, UUID resourceId) {
        return resourceId == null ? salonId.equals(closure.getSalonId()) : resourceId.equals(closure.getResourceId());
    }

    private static void requireValidPeriod(Instant start, Instant end) {
        if (!end.isAfter(start)) {
            throw new InvalidTimeRangeException("La fin doit être postérieure au début.");
        }
    }

    private static Instant parseInstant(String value) {
        try {
            return OffsetDateTime.parse(value.trim()).toInstant();
        } catch (DateTimeParseException | NullPointerException e) {
            throw new InvalidTimeRangeException("Date/heure invalide (attendu ISO 8601 avec offset) : " + value);
        }
    }

    private static String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? null : reason.trim();
    }
}
