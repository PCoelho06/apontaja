package com.apontaja.back.appointment.web;

import com.apontaja.back.appointment.application.AppointmentNotCancellableException;
import com.apontaja.back.appointment.application.CancellationDeadlinePassedException;
import com.apontaja.back.appointment.application.ClientAppointment;
import com.apontaja.back.appointment.application.ClientAppointmentNotFoundException;
import com.apontaja.back.appointment.application.ClientAppointmentPage;
import com.apontaja.back.appointment.application.ClientAppointmentScope;
import com.apontaja.back.appointment.application.ClientAppointmentService;
import com.apontaja.back.appointment.application.InvalidClientAppointmentQueryException;
import com.apontaja.back.web.PageResponse;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;

/**
 * « Mes rendez-vous » du client connecté (Bearer, CSRF exempt). Pas de {@code @PreAuthorize} de rôle : tout est
 * scopé par le compte authentifié, un RDV d'un autre compte est introuvable (404 uniforme). Les refus d'annulation
 * portent une propriété {@code code} stable.
 */
@RestController
@RequestMapping("/api/me/appointments")
class ClientAppointmentController {

    private static final String APPOINTMENT_ID = "appointmentId";

    private final ClientAppointmentService service;

    ClientAppointmentController(ClientAppointmentService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<PageResponse<ClientAppointmentResponse>> list(
            @RequestParam(required = false) String scope, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @AuthenticationPrincipal UUID accountId) {
        ClientAppointmentPage result = service.list(accountId, ClientAppointmentScope.parse(scope), page, size);
        PageResponse<ClientAppointmentResponse> body = new PageResponse<>(
                result.content().stream().map(ClientAppointmentController::toResponse).toList(), result.page(),
                result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    @PatchMapping("/{appointmentId}/cancel")
    public ResponseEntity<ClientAppointmentResponse> cancel(@PathVariable UUID appointmentId,
            @AuthenticationPrincipal UUID accountId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(toResponse(service.cancel(accountId, appointmentId)));
    }

    @ExceptionHandler(ClientAppointmentNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(ClientAppointmentNotFoundException ex) {
        return notFound();
    }

    /** UUID mal formé dans le chemin : même 404 ; page ou taille non numériques : 400. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        if (APPOINTMENT_ID.equals(ex.getName())) {
            return notFound();
        }
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Paramètre invalide : " + ex.getName() + "."));
    }

    @ExceptionHandler(InvalidClientAppointmentQueryException.class)
    ResponseEntity<ProblemDetail> handleInvalidQuery(InvalidClientAppointmentQueryException ex) {
        return ResponseEntity.badRequest()
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(AppointmentNotCancellableException.class)
    ResponseEntity<ProblemDetail> handleNotCancellable(AppointmentNotCancellableException ex) {
        return conflict(ex, "CANCELLATION_NOT_ALLOWED");
    }

    @ExceptionHandler(CancellationDeadlinePassedException.class)
    ResponseEntity<ProblemDetail> handleDeadlinePassed(CancellationDeadlinePassedException ex) {
        return conflict(ex, "CANCELLATION_DEADLINE_PASSED");
    }

    private static ResponseEntity<ProblemDetail> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Introuvable."));
    }

    private static ResponseEntity<ProblemDetail> conflict(RuntimeException ex, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setProperty("code", code);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    private static ClientAppointmentResponse toResponse(ClientAppointment a) {
        return new ClientAppointmentResponse(a.appointmentId(),
                new ClientAppointmentResponse.SalonRef(a.salon().id(), a.salon().name(), a.salon().city(),
                        a.salon().timezone()),
                new ClientAppointmentResponse.ServiceRef(a.service().id(), a.service().name()),
                new ClientAppointmentResponse.ResourceRef(a.resource().id(), a.resource().name()), a.startAt(),
                a.endAt(), a.status(), a.priceCents(), a.durationMinutes(), a.cancellable(),
                a.cancellableUntil(), a.cancelledAt());
    }
}
