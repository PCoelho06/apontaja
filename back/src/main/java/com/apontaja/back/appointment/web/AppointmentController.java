package com.apontaja.back.appointment.web;

import com.apontaja.back.appointment.application.AppointmentConflictException;
import com.apontaja.back.appointment.application.AppointmentCreationService;
import com.apontaja.back.appointment.application.AppointmentNotAvailableException;
import com.apontaja.back.appointment.application.AppointmentQueryService;
import com.apontaja.back.appointment.application.AppointmentSummary;
import com.apontaja.back.appointment.application.CreateAppointmentCommand;
import com.apontaja.back.appointment.application.CustomerNotFoundException;
import com.apontaja.back.appointment.application.ServiceResourceNotAssociatedException;
import com.apontaja.back.resource.application.InvalidTimeRangeException;
import com.apontaja.back.resource.application.ResourceNotFoundException;
import com.apontaja.back.service.application.ServiceNotFoundException;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Création et détail d'un RDV (Phase 3, tranche 6). Liste/agenda et
 * annulation/statuts : tranche 7. Accès : tout le staff du salon
 * ({@code salonAccessGuard}), conforme à Q2 — prendre un RDV est une opération
 * quotidienne, pas une configuration du salon.
 */
@RestController
@RequestMapping("/api/salons/{salonId}/appointments")
class AppointmentController {

    private static final String CAN_BOOK = "@salonAccessGuard.hasAccessToSalon(authentication.principal, #salonId)";

    private final AppointmentCreationService creationService;
    private final AppointmentQueryService queryService;

    AppointmentController(AppointmentCreationService creationService, AppointmentQueryService queryService) {
        this.creationService = creationService;
        this.queryService = queryService;
    }

    @PostMapping
    @PreAuthorize(CAN_BOOK)
    public ResponseEntity<AppointmentResponse> create(@P("salonId") @PathVariable UUID salonId,
            @Valid @RequestBody CreateAppointmentRequest request) {
        AppointmentSummary created = creationService.create(new CreateAppointmentCommand(salonId,
                request.customerProfileId(), request.serviceId(), request.resourceId(), request.startAt()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @GetMapping("/{appointmentId}")
    @PreAuthorize(CAN_BOOK)
    public ResponseEntity<AppointmentResponse> get(@P("salonId") @PathVariable UUID salonId,
            @PathVariable UUID appointmentId) {
        return queryService.findAliveInSalon(salonId, appointmentId).map(AppointmentController::toResponse)
                .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @ExceptionHandler({ResourceNotFoundException.class, ServiceNotFoundException.class,
            CustomerNotFoundException.class})
    ResponseEntity<ProblemDetail> handleNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler({ServiceResourceNotAssociatedException.class, InvalidTimeRangeException.class})
    ResponseEntity<ProblemDetail> handleBadRequest(RuntimeException ex) {
        return ResponseEntity.badRequest()
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(AppointmentNotAvailableException.class)
    ResponseEntity<ProblemDetail> handleNotAvailable(AppointmentNotAvailableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setProperty("issues", ex.issues().stream().map(Enum::name).toList());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(AppointmentConflictException.class)
    ResponseEntity<ProblemDetail> handleConflict(AppointmentConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage()));
    }

    private static AppointmentResponse toResponse(AppointmentSummary s) {
        return new AppointmentResponse(s.appointmentId(), s.salonId(), s.customerProfileId(), s.serviceId(),
                s.resourceId(), s.startAt(), s.endAt(), s.status(), s.priceAtBookingCents(),
                s.durationAtBookingMinutes(), s.createdAt());
    }
}
