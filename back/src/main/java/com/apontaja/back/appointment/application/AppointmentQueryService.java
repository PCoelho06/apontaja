package com.apontaja.back.appointment.application;

import com.apontaja.back.appointment.domain.Appointment;
import com.apontaja.back.appointment.domain.AppointmentRepository;
import com.apontaja.back.appointment.domain.AppointmentResource;
import com.apontaja.back.appointment.domain.AppointmentResourceRepository;
import com.apontaja.back.resource.application.InvalidTimeRangeException;
import com.apontaja.back.resource.application.ResourceNotFoundException;
import com.apontaja.back.resource.application.ResourceQueryService;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AppointmentQueryService {

    private static final Instant MIN_BOUND = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant MAX_BOUND = Instant.parse("9999-12-31T23:59:59Z");

    private final AppointmentRepository appointmentRepository;
    private final AppointmentResourceRepository appointmentResourceRepository;
    private final ResourceQueryService resourceQueryService;

    AppointmentQueryService(AppointmentRepository appointmentRepository,
            AppointmentResourceRepository appointmentResourceRepository, ResourceQueryService resourceQueryService) {
        this.appointmentRepository = appointmentRepository;
        this.appointmentResourceRepository = appointmentResourceRepository;
        this.resourceQueryService = resourceQueryService;
    }

    /** Vide si le RDV n'existe pas ou appartient à un autre salon. */
    public Optional<AppointmentSummary> findAliveInSalon(UUID salonId, UUID appointmentId) {
        return appointmentRepository.findAliveById(appointmentId).filter(a -> a.getSalonId().equals(salonId))
                .map(a -> AppointmentSummary.of(a, requireSoleResourceId(a.getId())));
    }

    /**
     * Agenda du salon recouvrant [from, to) (bornes ouvertes par défaut, mêmes
     * conventions que ClosureManagementService.list), filtré en option sur une
     * ressource. resourceId doit appartenir au salon (404 sinon) — même garde que
     * pour la réservation.
     */
    public List<AppointmentSummary> list(UUID salonId, UUID resourceId, String from, String to) {
        if (resourceId != null) {
            resourceQueryService.findAliveInSalon(salonId, resourceId).orElseThrow(ResourceNotFoundException::new);
        }
        Instant fromInstant = from == null ? MIN_BOUND : parseInstant(from);
        Instant toInstant = to == null ? MAX_BOUND : parseInstant(to);
        if (!toInstant.isAfter(fromInstant)) {
            throw new InvalidTimeRangeException("La fin doit être postérieure au début.");
        }

        List<Appointment> appointments = appointmentRepository.findOverlappingBySalonId(salonId, resourceId,
                fromInstant, toInstant);

        Map<UUID, UUID> resourceIdByAppointmentId = appointmentResourceRepository
                .findByAppointmentIds(appointments.stream().map(Appointment::getId).toList()).stream()
                .collect(Collectors.toMap(AppointmentResource::getAppointmentId, AppointmentResource::getResourceId,
                        (first, second) -> first));

        return appointments.stream().map(a -> AppointmentSummary.of(a, requireResourceId(resourceIdByAppointmentId, a)))
                .toList();
    }

    /**
     * v1 : invariant "toujours exactement une ressource par RDV" garanti par
     * AppointmentCreationService — sa violation serait un bug de données, jamais
     * un cas utilisateur normal.
     */
    private UUID requireSoleResourceId(UUID appointmentId) {
        return appointmentResourceRepository.findResourceIdsByAppointmentId(appointmentId).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Aucune ressource associée au rendez-vous " + appointmentId + " : incohérence de données."));
    }

    private static UUID requireResourceId(Map<UUID, UUID> resourceIdByAppointmentId, Appointment appointment) {
        UUID resourceId = resourceIdByAppointmentId.get(appointment.getId());
        if (resourceId == null) {
            throw new IllegalStateException("Aucune ressource associée au rendez-vous " + appointment.getId()
                    + " : incohérence de données.");
        }
        return resourceId;
    }

    private static Instant parseInstant(String value) {
        try {
            return OffsetDateTime.parse(value.trim()).toInstant();
        } catch (DateTimeParseException | NullPointerException e) {
            throw new InvalidTimeRangeException("Date/heure invalide (attendu ISO 8601 avec offset) : " + value);
        }
    }
}
