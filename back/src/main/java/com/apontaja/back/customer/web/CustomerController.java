package com.apontaja.back.customer.web;

import com.apontaja.back.customer.application.ContactRequiredException;
import com.apontaja.back.customer.application.CreateCustomerCommand;
import com.apontaja.back.customer.application.CustomerManagementService;
import com.apontaja.back.customer.application.CustomerQueryService;
import com.apontaja.back.customer.application.CustomerSummary;

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

import java.util.List;
import java.util.UUID;

/**
 * Carnet client minimal (Phase 3, tranche 5) : création manuelle, liste,
 * détail. Pas de modification ni de suppression — reporté à la Phase 4 (carnet
 * client complet).
 *
 * <p>
 * Accès (écriture ET lecture) : tout le staff du salon via
 * {@code salonAccessGuard} — décision volontairement plus large que
 * {@code catalogManagementGuard} (réservé OWNER/MANAGER) : accueillir un
 * client est une opération quotidienne, pas une configuration du salon, même
 * principe que la prise de RDV (§2 du contexte).
 */
@RestController
@RequestMapping("/api/salons/{salonId}/customers")
class CustomerController {

    private static final String CAN_READ_OR_WRITE =
            "@salonAccessGuard.hasAccessToSalon(authentication.principal, #salonId)";

    private final CustomerManagementService managementService;
    private final CustomerQueryService queryService;

    CustomerController(CustomerManagementService managementService, CustomerQueryService queryService) {
        this.managementService = managementService;
        this.queryService = queryService;
    }

    @PostMapping
    @PreAuthorize(CAN_READ_OR_WRITE)
    public ResponseEntity<CustomerResponse> create(@P("salonId") @PathVariable UUID salonId,
            @Valid @RequestBody CreateCustomerRequest request) {
        CustomerSummary created = managementService.create(new CreateCustomerCommand(salonId, request.firstName(),
                request.lastName(), request.email(), request.phone(), request.internalNotes()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @GetMapping
    @PreAuthorize(CAN_READ_OR_WRITE)
    public ResponseEntity<List<CustomerResponse>> list(@P("salonId") @PathVariable UUID salonId) {
        return ResponseEntity
                .ok(queryService.findAliveBySalonId(salonId).stream().map(CustomerController::toResponse).toList());
    }

    @GetMapping("/{customerProfileId}")
    @PreAuthorize(CAN_READ_OR_WRITE)
    public ResponseEntity<CustomerResponse> get(@P("salonId") @PathVariable UUID salonId,
            @PathVariable UUID customerProfileId) {
        return queryService.findAliveInSalon(salonId, customerProfileId).map(CustomerController::toResponse)
                .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @ExceptionHandler(ContactRequiredException.class)
    ResponseEntity<ProblemDetail> handleContactRequired(ContactRequiredException ex) {
        return ResponseEntity.badRequest()
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    private static CustomerResponse toResponse(CustomerSummary s) {
        return new CustomerResponse(s.customerProfileId(), s.salonCustomerLinkId(), s.salonId(), s.firstName(),
                s.lastName(), s.email(), s.phone(), s.internalNotes(), s.createdAt());
    }
}
