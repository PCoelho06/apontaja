package com.apontaja.back.resource.web;

import com.apontaja.back.resource.application.ClosureCommand;
import com.apontaja.back.resource.application.ClosureManagementService;
import com.apontaja.back.resource.application.ClosureNotFoundException;
import com.apontaja.back.resource.application.ClosureSummary;
import com.apontaja.back.resource.application.InvalidTimeRangeException;
import com.apontaja.back.resource.application.ResourceNotFoundException;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Fermetures exceptionnelles (salon et ressource). Écriture : OWNER/MANAGER (ou
 * OWNER d'organisation) ; lecture : tout le staff. Méthodes gardées
 * {@code public}. Dates en entrée : ISO 8601 avec offset.
 */
@RestController
@RequestMapping("/api/salons/{salonId}")
class ClosureController {

    private static final String CAN_MANAGE =
            "@catalogManagementGuard.canManageCatalog(authentication.principal, #salonId)";
    private static final String CAN_READ = "@salonAccessGuard.hasAccessToSalon(authentication.principal, #salonId)";

    private final ClosureManagementService closureService;

    ClosureController(ClosureManagementService closureService) {
        this.closureService = closureService;
    }

    // ---- fermetures du salon ----

    @PostMapping("/closures")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ClosureResponse> createSalonClosure(@P("salonId") @PathVariable UUID salonId,
            @Valid @RequestBody ClosureRequest request) {
        return created(closureService.create(salonId, null, toCommand(request)));
    }

    @GetMapping("/closures")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<List<ClosureResponse>> listSalonClosures(@P("salonId") @PathVariable UUID salonId,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return ResponseEntity.ok(toResponses(closureService.list(salonId, null, from, to)));
    }

    @PutMapping("/closures/{closureId}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ClosureResponse> updateSalonClosure(@P("salonId") @PathVariable UUID salonId,
            @PathVariable UUID closureId, @Valid @RequestBody ClosureRequest request) {
        return ResponseEntity.ok(toResponse(closureService.update(salonId, null, closureId, toCommand(request))));
    }

    @DeleteMapping("/closures/{closureId}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<Void> deleteSalonClosure(@P("salonId") @PathVariable UUID salonId,
            @PathVariable UUID closureId) {
        closureService.delete(salonId, null, closureId);
        return ResponseEntity.noContent().build();
    }

    // ---- fermetures d'une ressource ----

    @PostMapping("/resources/{resourceId}/closures")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ClosureResponse> createResourceClosure(@P("salonId") @PathVariable UUID salonId,
            @PathVariable UUID resourceId, @Valid @RequestBody ClosureRequest request) {
        return created(closureService.create(salonId, resourceId, toCommand(request)));
    }

    @GetMapping("/resources/{resourceId}/closures")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<List<ClosureResponse>> listResourceClosures(@P("salonId") @PathVariable UUID salonId,
            @PathVariable UUID resourceId, @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(toResponses(closureService.list(salonId, resourceId, from, to)));
    }

    @PutMapping("/resources/{resourceId}/closures/{closureId}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ClosureResponse> updateResourceClosure(@P("salonId") @PathVariable UUID salonId,
            @PathVariable UUID resourceId, @PathVariable UUID closureId, @Valid @RequestBody ClosureRequest request) {
        return ResponseEntity
                .ok(toResponse(closureService.update(salonId, resourceId, closureId, toCommand(request))));
    }

    @DeleteMapping("/resources/{resourceId}/closures/{closureId}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<Void> deleteResourceClosure(@P("salonId") @PathVariable UUID salonId,
            @PathVariable UUID resourceId, @PathVariable UUID closureId) {
        closureService.delete(salonId, resourceId, closureId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(InvalidTimeRangeException.class)
    ResponseEntity<ProblemDetail> handleInvalid(InvalidTimeRangeException ex) {
        return ResponseEntity.badRequest()
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler({ResourceNotFoundException.class, ClosureNotFoundException.class})
    ResponseEntity<ProblemDetail> handleNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    private static ResponseEntity<ClosureResponse> created(ClosureSummary summary) {
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(summary));
    }

    private static ClosureCommand toCommand(ClosureRequest request) {
        return new ClosureCommand(request.startAt(), request.endAt(), request.reason());
    }

    private static List<ClosureResponse> toResponses(List<ClosureSummary> summaries) {
        return summaries.stream().map(ClosureController::toResponse).toList();
    }

    private static ClosureResponse toResponse(ClosureSummary s) {
        return new ClosureResponse(s.closureId(), s.salonId(), s.resourceId(), s.startAt(), s.endAt(), s.reason(),
                s.createdAt());
    }
}
