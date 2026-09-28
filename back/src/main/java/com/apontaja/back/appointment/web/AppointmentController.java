package com.apontaja.back.appointment.web;

import com.apontaja.back.appointment.application.AppointmentConflictException;
import com.apontaja.back.appointment.application.AppointmentCreationService;
import com.apontaja.back.appointment.application.AppointmentNotAvailableException;
import com.apontaja.back.appointment.application.AppointmentNotFoundException;
import com.apontaja.back.appointment.application.AppointmentQueryService;
import com.apontaja.back.appointment.application.AppointmentStatusService;
import com.apontaja.back.appointment.application.AppointmentSummary;
import com.apontaja.back.appointment.application.CreateAppointmentCommand;
import com.apontaja.back.appointment.application.CustomerNotFoundException;
import com.apontaja.back.appointment.application.InvalidAppointmentTransitionException;
import com.apontaja.back.appointment.application.ServiceResourceNotAssociatedException;
import com.apontaja.back.resource.application.InvalidTimeRangeException;
import com.apontaja.back.resource.application.ResourceNotFoundException;
import com.apontaja.back.service.application.ServiceNotFoundException;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Création, détail, agenda et transitions de statut d'un RDV (Phase 3,
 * tranches 6-7). Modification (déplacement) : tranche 8. Accès : tout le
 * staff du salon ({@code salonAccessGuard}) pour toutes les opérations,
 * conforme à Q2 — prendre, consulter, confirmer ou annuler un RDV sont des
 * opérations quotidiennes, pas une configuration du salon.
 */
@RestController
@RequestMapping("/api/salons/{salonId}/appointments")
class AppointmentController {

    private static final String CAN_BOOK = "@salonAccessGuard.hasAccessToSalon(authentication.principal, #salonId)";

    private final AppointmentCreationService creationService;
    private final AppointmentQueryService queryService;
    private final AppointmentStatusService statusService;

    AppointmentController(AppointmentCreationService creationService, AppointmentQueryService queryService,
            AppointmentStatusService statusService) {
        this.creationService = creationService;
        this.queryService = queryService;
        this.statusService = statusService;
    }

    @PostMapping
    @PreAuthorize(CAN_BOOK)
    public ResponseEntity<AppointmentResponse> create(@P("salonId") @PathVariable UUID salonId,
            @Valid @RequestBody CreateAppointmentRequest request) {
        AppointmentSummary created = creationService.create(new CreateAppointmentCommand(salonId,
                request.customerProfileId(), request.serviceId(), request.resourceId(), request.startAt()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @GetMapping
    @PreAuthorize(CAN_BOOK)
    public ResponseEntity<List<AppointmentResponse>> list(@P("salonId") @PathVariable UUID salonId,
            @RequestParam(required = false) UUID resourceId, @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity
                .ok(queryService.list(salonId, resourceId, from, to).stream().map(AppointmentController::toResponse)
                        .toList());
    }

    @GetMapping("/{appointmentId}")
    @PreAuthorize(CAN_BOOK)
    public ResponseEntity<AppointmentResponse> get(@P("salonId") @PathVariable UUID salonId,
            @PathVariable UUID appointmentId) {
        return queryService.findAliveInSalon(salonId, appointmentId).map(AppointmentController::toResponse)
                .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PatchMapping("/{appointmentId}/confirm")
    @PreAuthorize(CAN_BOOK)
    public ResponseEntity<AppointmentResponse> confirm(@P("salonId") @PathVariable UUID salonId,
            @PathVariable UUID appointmentId) {
        return ResponseEntity.ok(toResponse(statusService.confirm(salonId, appointmentId)));
    }

    @PatchMapping("/{appointmentId}/cancel")
    @PreAuthorize(CAN_BOOK)
    public ResponseEntity<AppointmentResponse> cancel(@P("salonId") @PathVariable UUID salonId,
            @PathVariable UUID appointmentId, @Valid @RequestBody CancelAppointmentRequest request,
            @AuthenticationPrincipal UUID accountId) {
        return ResponseEntity
                .ok(toResponse(statusService.cancel(salonId, appointmentId, accountId, request.reason())));
    }

    @PatchMapping("/{appointmentId}/complete")
    @PreAuthorize(CAN_BOOK)
    public ResponseEntity<AppointmentResponse> complete(@P("salonId") @PathVariable UUID salonId,
            @PathVariable UUID appointmentId) {
        return ResponseEntity.ok(toResponse(statusService.complete(salonId, appointmentId)));
    }

    @PatchMapping("/{appointmentId}/no-show")
    @PreAuthorize(CAN_BOOK)
    public ResponseEntity<AppointmentResponse> markNoShow(@P("salonId") @PathVariable UUID salonId,
            @PathVariable UUID appointmentId) {
        return ResponseEntity.ok(toResponse(statusService.markNoShow(salonId, appointmentId)));
    }

    @ExceptionHandler({ResourceNotFoundException.class, ServiceNotFoundException.class,
            CustomerNotFoundException.class, AppointmentNotFoundException.class})
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

    @ExceptionHandler({AppointmentConflictException.class, InvalidAppointmentTransitionException.class})
    ResponseEntity<ProblemDetail> handleConflict(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage()));
    }

    private static AppointmentResponse toResponse(AppointmentSummary s) {
        return new AppointmentResponse(s.appointmentId(), s.salonId(), s.customerProfileId(), s.serviceId(),
                s.resourceId(), s.startAt(), s.endAt(), s.status(), s.priceAtBookingCents(),
                s.durationAtBookingMinutes(), s.createdAt());
    }
}
