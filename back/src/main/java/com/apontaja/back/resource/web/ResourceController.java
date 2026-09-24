package com.apontaja.back.resource.web;

import com.apontaja.back.resource.application.CreateResourceCommand;
import com.apontaja.back.resource.application.InvalidResourceTypeException;
import com.apontaja.back.resource.application.ResourceInUseException;
import com.apontaja.back.resource.application.ResourceManagementService;
import com.apontaja.back.resource.application.ResourceNameAlreadyUsedException;
import com.apontaja.back.resource.application.ResourceNotFoundException;
import com.apontaja.back.resource.application.ResourceQueryService;
import com.apontaja.back.resource.application.ResourceSummary;

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
 * 403 uniforme si le salon n'existe pas ou n'est pas accessible.
 */
@RestController
@RequestMapping("/api/salons/{salonId}/resources")
class ResourceController {

    private final ResourceManagementService managementService;
    private final ResourceQueryService queryService;

    ResourceController(ResourceManagementService managementService, ResourceQueryService queryService) {
        this.managementService = managementService;
        this.queryService = queryService;
    }

    @PostMapping
    @PreAuthorize("@catalogManagementGuard.canManageCatalog(authentication.principal, #salonId)")
    public ResponseEntity<ResourceResponse> create(@P("salonId") @PathVariable UUID salonId,
            @Valid @RequestBody CreateResourceRequest request) {
        ResourceSummary created = managementService
                .create(new CreateResourceCommand(salonId, request.name(), request.type()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @GetMapping
    @PreAuthorize("@salonAccessGuard.hasAccessToSalon(authentication.principal, #salonId)")
    public ResponseEntity<List<ResourceResponse>> list(@P("salonId") @PathVariable UUID salonId) {
        return ResponseEntity.ok(
                queryService.findAliveBySalonId(salonId).stream().map(ResourceController::toResponse).toList());
    }

    @GetMapping("/{resourceId}")
    @PreAuthorize("@salonAccessGuard.hasAccessToSalon(authentication.principal, #salonId)")
    public ResponseEntity<ResourceResponse> get(@P("salonId") @PathVariable UUID salonId,
            @PathVariable UUID resourceId) {
        return queryService.findAliveInSalon(salonId, resourceId).map(ResourceController::toResponse)
                .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PutMapping("/{resourceId}")
    @PreAuthorize("@catalogManagementGuard.canManageCatalog(authentication.principal, #salonId)")
    public ResponseEntity<ResourceResponse> update(@P("salonId") @PathVariable UUID salonId,
            @PathVariable UUID resourceId, @Valid @RequestBody UpdateResourceRequest request) {
        return ResponseEntity
                .ok(toResponse(managementService.update(salonId, resourceId, request.name(), request.type())));
    }

    @DeleteMapping("/{resourceId}")
    @PreAuthorize("@catalogManagementGuard.canManageCatalog(authentication.principal, #salonId)")
    public ResponseEntity<Void> delete(@P("salonId") @PathVariable UUID salonId, @PathVariable UUID resourceId) {
        managementService.delete(salonId, resourceId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler({ResourceNameAlreadyUsedException.class, ResourceInUseException.class})
    ResponseEntity<ProblemDetail> handleConflict(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(InvalidResourceTypeException.class)
    ResponseEntity<ProblemDetail> handleInvalidType(InvalidResourceTypeException ex) {
        return ResponseEntity.badRequest()
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    private static ResourceResponse toResponse(ResourceSummary s) {
        return new ResourceResponse(s.resourceId(), s.salonId(), s.name(), s.type(), s.createdAt());
    }
}
