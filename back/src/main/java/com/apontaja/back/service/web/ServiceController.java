package com.apontaja.back.service.web;

import com.apontaja.back.service.application.LinkedResourceNotFoundException;
import com.apontaja.back.service.application.ServiceDetails;
import com.apontaja.back.service.application.ServiceInUseException;
import com.apontaja.back.service.application.ServiceManagementService;
import com.apontaja.back.service.application.ServiceNameAlreadyUsedException;
import com.apontaja.back.service.application.ServiceNotFoundException;
import com.apontaja.back.service.application.ServiceQueryService;
import com.apontaja.back.service.application.ServiceResourceLinkConflictException;
import com.apontaja.back.service.application.ServiceResourceLinkService;
import com.apontaja.back.service.application.ServiceResourceLinkSummary;
import com.apontaja.back.service.application.ServiceSummary;

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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Écriture : OWNER/MANAGER (ou OWNER d'organisation) via catalogManagementGuard.
 * Lecture : tout le staff du salon via salonAccessGuard. Toutes les méthodes
 * gardées sont {@code public} (sinon @PreAuthorize est ignoré silencieusement).
 * Un seul contrôleur, handlers d'exceptions locaux : un @RestControllerAdvice
 * séparé pourrait être court-circuité par le catch-all de GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/salons/{salonId}/services")
class ServiceController {

    private static final String CAN_MANAGE =
            "@catalogManagementGuard.canManageCatalog(authentication.principal, #salonId)";
    private static final String CAN_READ = "@salonAccessGuard.hasAccessToSalon(authentication.principal, #salonId)";

    private final ServiceManagementService managementService;
    private final ServiceQueryService queryService;
    private final ServiceResourceLinkService linkService;

    ServiceController(ServiceManagementService managementService, ServiceQueryService queryService,
            ServiceResourceLinkService linkService) {
        this.managementService = managementService;
        this.queryService = queryService;
        this.linkService = linkService;
    }

    @PostMapping
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ServiceResponse> create(@P("salonId") @PathVariable UUID salonId,
            @Valid @RequestBody ServiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(managementService.create(salonId, toDetails(request))));
    }

    @GetMapping
    @PreAuthorize(CAN_READ)
    public ResponseEntity<List<ServiceResponse>> list(@P("salonId") @PathVariable UUID salonId) {
        return ResponseEntity
                .ok(queryService.findAliveBySalonId(salonId).stream().map(ServiceController::toResponse).toList());
    }

    @GetMapping("/{serviceId}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ServiceResponse> get(@P("salonId") @PathVariable UUID salonId,
            @PathVariable UUID serviceId) {
        return queryService.findAliveInSalon(salonId, serviceId).map(ServiceController::toResponse)
                .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PutMapping("/{serviceId}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ServiceResponse> update(@P("salonId") @PathVariable UUID salonId,
            @PathVariable UUID serviceId, @Valid @RequestBody ServiceRequest request) {
        return ResponseEntity.ok(toResponse(managementService.update(salonId, serviceId, toDetails(request))));
    }

    @DeleteMapping("/{serviceId}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<Void> delete(@P("salonId") @PathVariable UUID salonId, @PathVariable UUID serviceId) {
        managementService.delete(salonId, serviceId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{serviceId}/resources")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<List<ServiceResourceLinkResponse>> listLinks(@P("salonId") @PathVariable UUID salonId,
            @PathVariable UUID serviceId) {
        return ResponseEntity.ok(
                linkService.list(salonId, serviceId).stream().map(ServiceController::toLinkResponse).toList());
    }

    @PutMapping("/{serviceId}/resources/{resourceId}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ServiceResourceLinkResponse> link(@P("salonId") @PathVariable UUID salonId,
            @PathVariable UUID serviceId, @PathVariable UUID resourceId,
            @Valid @RequestBody ServiceResourceLinkRequest request) {
        return ResponseEntity.ok(toLinkResponse(linkService.link(salonId, serviceId, resourceId,
                request.overridePriceCents(), request.overrideDurationMinutes())));
    }

    @DeleteMapping("/{serviceId}/resources/{resourceId}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<Void> unlink(@P("salonId") @PathVariable UUID salonId, @PathVariable UUID serviceId,
            @PathVariable UUID resourceId) {
        linkService.unlink(salonId, serviceId, resourceId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler({ServiceNameAlreadyUsedException.class, ServiceInUseException.class,
            ServiceResourceLinkConflictException.class})
    ResponseEntity<ProblemDetail> handleConflict(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage()));
    }

    @ExceptionHandler({ServiceNotFoundException.class, LinkedResourceNotFoundException.class})
    ResponseEntity<ProblemDetail> handleNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    private static ServiceDetails toDetails(ServiceRequest request) {
        return new ServiceDetails(request.name(), request.description(), request.defaultDurationMinutes(),
                request.defaultPriceCents());
    }

    private static ServiceResponse toResponse(ServiceSummary s) {
        return new ServiceResponse(s.serviceId(), s.salonId(), s.name(), s.description(),
                s.defaultDurationMinutes(), s.defaultPriceCents(), s.createdAt());
    }

    private static ServiceResourceLinkResponse toLinkResponse(ServiceResourceLinkSummary l) {
        return new ServiceResourceLinkResponse(l.resourceId(), l.overridePriceCents(), l.overrideDurationMinutes(),
                l.effectivePriceCents(), l.effectiveDurationMinutes());
    }
}
