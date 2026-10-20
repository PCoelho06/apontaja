package com.apontaja.back.customer.web;

import com.apontaja.back.customer.application.ContactRequiredException;
import com.apontaja.back.customer.application.CreateCustomerCommand;
import com.apontaja.back.customer.application.CustomerAnonymizedException;
import com.apontaja.back.customer.application.CustomerBookQueryService;
import com.apontaja.back.customer.application.CustomerCard;
import com.apontaja.back.customer.application.CustomerCardPage;
import com.apontaja.back.customer.application.CustomerEditionService;
import com.apontaja.back.customer.application.CustomerHasUpcomingAppointmentsException;
import com.apontaja.back.customer.application.CustomerHistoryItem;
import com.apontaja.back.customer.application.CustomerHistoryPage;
import com.apontaja.back.customer.application.CustomerManagementService;
import com.apontaja.back.customer.application.CustomerNotFoundException;
import com.apontaja.back.customer.application.CustomerSharedException;
import com.apontaja.back.customer.application.UpdateCustomerCommand;
import com.apontaja.back.web.PageResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
 * Carnet client d'un salon : création, liste paginée avec recherche, fiche, modification, consentement marketing,
 * historique des RDV et anonymisation (droit à l'effacement).
 *
 * <p>
 * Accès : tout le staff du salon via {@code salonAccessGuard} (accueillir un client est une opération
 * quotidienne), sauf l'anonymisation, irréversible, réservée aux OWNER/MANAGER (même règle que le catalogue :
 * {@code catalogManagementGuard}).
 */
@RestController
@RequestMapping("/api/salons/{salonId}/customers")
class CustomerController {

    private static final String CAN_READ_OR_WRITE =
            "@salonAccessGuard.hasAccessToSalon(authentication.principal, #salonId)";
    private static final String CAN_ANONYMIZE =
            "@catalogManagementGuard.canManageCatalog(authentication.principal, #salonId)";

    private final CustomerManagementService managementService;
    private final CustomerEditionService editionService;
    private final CustomerBookQueryService bookQueryService;

    CustomerController(CustomerManagementService managementService, CustomerEditionService editionService,
            CustomerBookQueryService bookQueryService) {
        this.managementService = managementService;
        this.editionService = editionService;
        this.bookQueryService = bookQueryService;
    }

    @PostMapping
    @PreAuthorize(CAN_READ_OR_WRITE)
    public ResponseEntity<CustomerResponse> create(@P("salonId") @PathVariable UUID salonId,
            @Valid @RequestBody CreateCustomerRequest request) {
        CustomerCard created = CustomerCard.ofNew(managementService.create(new CreateCustomerCommand(salonId,
                request.firstName(), request.lastName(), request.email(), request.phone(),
                request.internalNotes())));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @GetMapping
    @PreAuthorize(CAN_READ_OR_WRITE)
    public ResponseEntity<PageResponse<CustomerResponse>> list(@P("salonId") @PathVariable UUID salonId,
            @RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CustomerCardPage result = bookQueryService.search(salonId, q, page, size);
        List<CustomerResponse> content = result.cards().stream().map(CustomerController::toResponse).toList();
        return ResponseEntity.ok(new PageResponse<>(content, result.page(), result.size(), result.totalElements(),
                totalPages(result.totalElements(), result.size())));
    }

    @GetMapping("/{customerProfileId}")
    @PreAuthorize(CAN_READ_OR_WRITE)
    public ResponseEntity<CustomerResponse> get(@P("salonId") @PathVariable UUID salonId,
            @PathVariable UUID customerProfileId) {
        return bookQueryService.find(salonId, customerProfileId).map(CustomerController::toResponse)
                .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @PutMapping("/{customerProfileId}")
    @PreAuthorize(CAN_READ_OR_WRITE)
    public ResponseEntity<CustomerResponse> update(@P("salonId") @PathVariable UUID salonId,
            @PathVariable UUID customerProfileId, @Valid @RequestBody UpdateCustomerRequest request) {
        CustomerCard updated = editionService.update(new UpdateCustomerCommand(salonId, customerProfileId,
                request.firstName(), request.lastName(), request.email(), request.phone(),
                request.internalNotes()));
        return ResponseEntity.ok(toResponse(updated));
    }

    @PatchMapping("/{customerProfileId}/marketing-consent")
    @PreAuthorize(CAN_READ_OR_WRITE)
    public ResponseEntity<CustomerResponse> setMarketingConsent(@P("salonId") @PathVariable UUID salonId,
            @PathVariable UUID customerProfileId, @Valid @RequestBody MarketingConsentRequest request) {
        return ResponseEntity.ok(toResponse(
                editionService.setMarketingConsent(salonId, customerProfileId, request.consented())));
    }

    @DeleteMapping("/{customerProfileId}")
    @PreAuthorize(CAN_ANONYMIZE)
    public ResponseEntity<Void> anonymize(@P("salonId") @PathVariable UUID salonId,
            @PathVariable UUID customerProfileId) {
        editionService.anonymize(salonId, customerProfileId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{customerProfileId}/appointments")
    @PreAuthorize(CAN_READ_OR_WRITE)
    public ResponseEntity<PageResponse<CustomerHistoryItemResponse>> history(
            @P("salonId") @PathVariable UUID salonId, @PathVariable UUID customerProfileId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        CustomerHistoryPage result = bookQueryService.history(salonId, customerProfileId, page, size);
        List<CustomerHistoryItemResponse> content = result.items().stream()
                .map(CustomerController::toHistoryResponse).toList();
        return ResponseEntity.ok(new PageResponse<>(content, result.page(), result.size(), result.totalElements(),
                totalPages(result.totalElements(), result.size())));
    }

    @ExceptionHandler(ContactRequiredException.class)
    ResponseEntity<ProblemDetail> handleContactRequired(ContactRequiredException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(CustomerNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({ CustomerAnonymizedException.class, CustomerSharedException.class,
            CustomerHasUpcomingAppointmentsException.class })
    ResponseEntity<ProblemDetail> handleConflict(RuntimeException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }

    private static int totalPages(long totalElements, int size) {
        return (int) ((totalElements + size - 1) / size);
    }

    static CustomerResponse toResponse(CustomerCard c) {
        return new CustomerResponse(c.customerProfileId(), c.salonCustomerLinkId(), c.salonId(), c.firstName(),
                c.lastName(), c.email(), c.phone(), c.internalNotes(), c.marketingConsent(),
                c.marketingConsentAt(), c.marketingConsentRevokedAt(), c.anonymized(), c.createdAt(),
                c.lastVisitAt(), c.nextVisitAt());
    }

    private static CustomerHistoryItemResponse toHistoryResponse(CustomerHistoryItem i) {
        return new CustomerHistoryItemResponse(i.appointmentId(), i.startAt(), i.endAt(), i.status(),
                i.serviceId(), i.priceAtBookingCents(), i.durationAtBookingMinutes());
    }
}
