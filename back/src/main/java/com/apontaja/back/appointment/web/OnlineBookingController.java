package com.apontaja.back.appointment.web;

import com.apontaja.back.appointment.application.AppointmentConflictException;
import com.apontaja.back.appointment.application.BookingQuotaExceededException;
import com.apontaja.back.appointment.application.OnlineBookingCommand;
import com.apontaja.back.appointment.application.OnlineBookingResult;
import com.apontaja.back.appointment.application.OnlineBookingService;
import com.apontaja.back.appointment.application.PublicBookingNotFoundException;
import com.apontaja.back.appointment.application.SlotUnavailableException;
import com.apontaja.back.customer.application.EmailNotVerifiedException;
import com.apontaja.back.customer.application.SharingConsentRequiredException;
import com.apontaja.back.resource.application.InvalidTimeRangeException;

import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;

/**
 * Réservation en ligne par le client connecté (Bearer, CSRF exempt). Pas de {@code @PreAuthorize} de rôle : tout
 * compte authentifié peut réserver, les règles (email vérifié, salon réservable, quota) sont dans le service. Les
 * erreurs métier portent une propriété {@code code} stable. Salon, prestation ou ressource inconnus : 404 uniforme.
 */
@RestController
@RequestMapping("/api/me/salons/{salonId}/bookings")
class OnlineBookingController {

    private final OnlineBookingService onlineBookingService;

    OnlineBookingController(OnlineBookingService onlineBookingService) {
        this.onlineBookingService = onlineBookingService;
    }

    @PostMapping
    public ResponseEntity<OnlineBookingResponse> book(@PathVariable UUID salonId,
            @Valid @RequestBody OnlineBookingRequest request, @AuthenticationPrincipal UUID accountId) {
        OnlineBookingResult result = onlineBookingService.book(new OnlineBookingCommand(accountId, salonId,
                request.serviceId(), request.resourceId(), request.startAt(), request.phone(),
                Boolean.TRUE.equals(request.shareContactConsent())));
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(new OnlineBookingResponse(result.appointmentId(), result.salonId(), result.serviceId(),
                        result.serviceName(), result.resourceId(), result.resourceName(), result.startAt(),
                        result.endAt(), result.status(), result.priceCents(), result.durationMinutes(),
                        result.createdAt()));
    }

    @ExceptionHandler({ PublicBookingNotFoundException.class, MethodArgumentTypeMismatchException.class })
    ResponseEntity<ProblemDetail> handleNotFound(Exception ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Introuvable."));
    }

    @ExceptionHandler(InvalidTimeRangeException.class)
    ResponseEntity<ProblemDetail> handleInvalid(InvalidTimeRangeException ex) {
        return ResponseEntity.badRequest()
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(SharingConsentRequiredException.class)
    ResponseEntity<ProblemDetail> handleConsentRequired(SharingConsentRequiredException ex) {
        return withCode(HttpStatus.BAD_REQUEST, ex, "SHARING_CONSENT_REQUIRED");
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    ResponseEntity<ProblemDetail> handleEmailNotVerified(EmailNotVerifiedException ex) {
        return withCode(HttpStatus.FORBIDDEN, ex, "EMAIL_NOT_VERIFIED");
    }

    @ExceptionHandler(BookingQuotaExceededException.class)
    ResponseEntity<ProblemDetail> handleQuota(BookingQuotaExceededException ex) {
        return withCode(HttpStatus.CONFLICT, ex, "BOOKING_QUOTA_EXCEEDED");
    }

    @ExceptionHandler({ SlotUnavailableException.class, AppointmentConflictException.class })
    ResponseEntity<ProblemDetail> handleSlotUnavailable(RuntimeException ex) {
        return withCode(HttpStatus.CONFLICT, ex, "SLOT_UNAVAILABLE");
    }

    private static ResponseEntity<ProblemDetail> withCode(HttpStatus status, RuntimeException ex, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setProperty("code", code);
        return ResponseEntity.status(status).body(problem);
    }
}
