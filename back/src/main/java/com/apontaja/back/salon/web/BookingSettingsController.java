package com.apontaja.back.salon.web;

import com.apontaja.back.salon.application.BookingSettingsService;
import com.apontaja.back.salon.application.InvalidBookingSettingsException;
import com.apontaja.back.salon.application.UpdateBookingSettingsCommand;

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

import java.util.UUID;

/**
 * Réglages de réservation en ligne du salon. Écriture : OWNER/MANAGER (ou OWNER d'organisation) ;
 * lecture : tout le staff. Méthodes gardées {@code public}.
 */
@RestController
@RequestMapping("/api/salons/{salonId}/booking-settings")
class BookingSettingsController {

    private static final String CAN_MANAGE =
            "@catalogManagementGuard.canManageCatalog(authentication.principal, #salonId)";
    private static final String CAN_READ = "@salonAccessGuard.hasAccessToSalon(authentication.principal, #salonId)";

    private final BookingSettingsService settingsService;

    BookingSettingsController(BookingSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    @PreAuthorize(CAN_READ)
    public ResponseEntity<BookingSettingsResponse> get(@P("salonId") @PathVariable UUID salonId) {
        return ResponseEntity.ok(BookingSettingsResponse.from(settingsService.get(salonId)));
    }

    @PutMapping
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<BookingSettingsResponse> replace(@P("salonId") @PathVariable UUID salonId,
            @Valid @RequestBody BookingSettingsRequest request) {
        UpdateBookingSettingsCommand command = new UpdateBookingSettingsCommand(salonId,
                request.onlineBookingEnabled(), request.autoConfirm(), request.slotGranularityMinutes(),
                request.minNoticeMinutes(), request.horizonDays(), request.cancellationDeadlineHours());
        return ResponseEntity.ok(BookingSettingsResponse.from(settingsService.update(command)));
    }

    @ExceptionHandler(InvalidBookingSettingsException.class)
    ResponseEntity<ProblemDetail> handleInvalid(InvalidBookingSettingsException ex) {
        return ResponseEntity.badRequest()
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }
}
