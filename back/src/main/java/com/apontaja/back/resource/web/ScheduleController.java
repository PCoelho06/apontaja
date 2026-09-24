package com.apontaja.back.resource.web;

import com.apontaja.back.resource.application.InvalidTimeRangeException;
import com.apontaja.back.resource.application.ResourceNotFoundException;
import com.apontaja.back.resource.application.ScheduleManagementService;
import com.apontaja.back.resource.application.ScheduleSlotCommand;
import com.apontaja.back.resource.application.ScheduleSlotSummary;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Horaires récurrents (salon et ressource). Écriture : OWNER/MANAGER (ou OWNER
 * d'organisation) ; lecture : tout le staff. Méthodes gardées {@code public}.
 */
@RestController
@RequestMapping("/api/salons/{salonId}")
class ScheduleController {

    private static final String CAN_MANAGE =
            "@catalogManagementGuard.canManageCatalog(authentication.principal, #salonId)";
    private static final String CAN_READ = "@salonAccessGuard.hasAccessToSalon(authentication.principal, #salonId)";

    private final ScheduleManagementService scheduleService;

    ScheduleController(ScheduleManagementService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping("/schedule")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ScheduleResponse> getSalonSchedule(@P("salonId") @PathVariable UUID salonId) {
        return ResponseEntity.ok(toResponse(scheduleService.getSalonSchedule(salonId)));
    }

    @PutMapping("/schedule")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ScheduleResponse> replaceSalonSchedule(@P("salonId") @PathVariable UUID salonId,
            @Valid @RequestBody ScheduleRequest request) {
        return ResponseEntity.ok(toResponse(scheduleService.replaceSalonSchedule(salonId, toCommands(request))));
    }

    @GetMapping("/resources/{resourceId}/schedule")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ScheduleResponse> getResourceSchedule(@P("salonId") @PathVariable UUID salonId,
            @PathVariable UUID resourceId) {
        return ResponseEntity.ok(toResponse(scheduleService.getResourceSchedule(salonId, resourceId)));
    }

    @PutMapping("/resources/{resourceId}/schedule")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ScheduleResponse> replaceResourceSchedule(@P("salonId") @PathVariable UUID salonId,
            @PathVariable UUID resourceId, @Valid @RequestBody ScheduleRequest request) {
        return ResponseEntity.ok(
                toResponse(scheduleService.replaceResourceSchedule(salonId, resourceId, toCommands(request))));
    }

    @ExceptionHandler(InvalidTimeRangeException.class)
    ResponseEntity<ProblemDetail> handleInvalid(InvalidTimeRangeException ex) {
        return ResponseEntity.badRequest()
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    private static List<ScheduleSlotCommand> toCommands(ScheduleRequest request) {
        return request.slots().stream()
                .map(s -> new ScheduleSlotCommand(s.dayOfWeek(), s.startTime(), s.endTime())).toList();
    }

    private static ScheduleResponse toResponse(List<ScheduleSlotSummary> slots) {
        return new ScheduleResponse(slots.stream().map(s -> new ScheduleSlotResponse(s.dayOfWeek().name(),
                s.startTime().toString(), s.endTime().toString())).toList());
    }
}
