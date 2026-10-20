package com.apontaja.back.appointment.web;

import com.apontaja.back.appointment.application.PublicBooking;
import com.apontaja.back.appointment.application.PublicBookingNotFoundException;
import com.apontaja.back.appointment.application.PublicBookingService;
import com.apontaja.back.resource.application.InvalidTimeRangeException;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.UUID;

/**
 * Réservation en ligne, lecture publique (aucune authentification : {@code permitAll} dans
 * SecurityConfig, limitation de débit par IP dans RateLimitingFilter). Les réponses sont les vues
 * {@link PublicBooking} : aucune donnée personnelle. Tout identifiant inconnu ou mal formé donne le même
 * 404 sans détail.
 */
@RestController
@RequestMapping("/api/public/salons/{salonId}")
class PublicBookingController {

    private final PublicBookingService publicBookingService;

    PublicBookingController(PublicBookingService publicBookingService) {
        this.publicBookingService = publicBookingService;
    }

    @GetMapping
    public ResponseEntity<PublicBooking.SalonInfo> salon(@PathVariable UUID salonId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(publicBookingService.salon(salonId));
    }

    @GetMapping("/services")
    public ResponseEntity<List<PublicBooking.Offering>> services(@PathVariable UUID salonId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(publicBookingService.services(salonId));
    }

    /** Paramètres from/to facultatifs côté Spring pour que l'absence donne un 400 métier (pas un 500). */
    @GetMapping("/services/{serviceId}/slots")
    public ResponseEntity<PublicBooking.Slots> slots(@PathVariable UUID salonId, @PathVariable UUID serviceId,
            @RequestParam(required = false) UUID resourceId, @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(publicBookingService.slots(salonId, serviceId, resourceId, from, to));
    }

    @ExceptionHandler({PublicBookingNotFoundException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> handleNotFound(Exception ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Introuvable."));
    }

    @ExceptionHandler(InvalidTimeRangeException.class)
    ResponseEntity<ProblemDetail> handleInvalid(InvalidTimeRangeException ex) {
        return ResponseEntity.badRequest()
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }
}
