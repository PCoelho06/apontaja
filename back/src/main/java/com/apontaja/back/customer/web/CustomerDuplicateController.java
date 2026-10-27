package com.apontaja.back.customer.web;

import com.apontaja.back.customer.application.CustomerAnonymizedException;
import com.apontaja.back.customer.application.CustomerClaimedException;
import com.apontaja.back.customer.application.CustomerDuplicateQueryService;
import com.apontaja.back.customer.application.CustomerMergeService;
import com.apontaja.back.customer.application.CustomerNotFoundException;
import com.apontaja.back.customer.application.CustomerSharedException;
import com.apontaja.back.customer.application.InvalidMergeRequestException;
import com.apontaja.back.customer.application.MergeCustomersCommand;
import com.apontaja.back.web.ApiErrorCode;
import com.apontaja.back.web.ApiProblems;

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

import java.util.UUID;

/**
 * Doublons du carnet client et fusion. Réservé aux OWNER/MANAGER (et OWNER d'organisation), comme l'anonymisation :
 * la fusion est irréversible. Les chemins littéraux {@code duplicates} et {@code merge} l'emportent sur
 * {@code /{customerProfileId}} du contrôleur du carnet.
 */
@RestController
@RequestMapping("/api/salons/{salonId}/customers")
class CustomerDuplicateController {

    private static final String CAN_MERGE =
            "@catalogManagementGuard.canManageCatalog(authentication.principal, #salonId)";

    private final CustomerDuplicateQueryService duplicateQueryService;
    private final CustomerMergeService mergeService;

    CustomerDuplicateController(CustomerDuplicateQueryService duplicateQueryService,
            CustomerMergeService mergeService) {
        this.duplicateQueryService = duplicateQueryService;
        this.mergeService = mergeService;
    }

    @GetMapping("/duplicates")
    @PreAuthorize(CAN_MERGE)
    public ResponseEntity<DuplicateReportResponse> duplicates(@P("salonId") @PathVariable UUID salonId) {
        return ResponseEntity.ok(DuplicateReportResponse.of(duplicateQueryService.findDuplicates(salonId)));
    }

    @PostMapping("/merge")
    @PreAuthorize(CAN_MERGE)
    public ResponseEntity<CustomerResponse> merge(@P("salonId") @PathVariable UUID salonId,
            @Valid @RequestBody MergeCustomersRequest request) {
        return ResponseEntity.ok(CustomerController.toResponse(mergeService
                .merge(new MergeCustomersCommand(salonId, request.survivorId(), request.absorbedIds()))));
    }

    @ExceptionHandler(InvalidMergeRequestException.class)
    ResponseEntity<ProblemDetail> handleInvalid(InvalidMergeRequestException ex) {
        return ApiProblems.of(HttpStatus.BAD_REQUEST, ex.getMessage(), ApiErrorCode.INVALID_MERGE_REQUEST);
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(CustomerNotFoundException ex) {
        return ApiProblems.of(HttpStatus.NOT_FOUND, ex.getMessage(), ApiErrorCode.CUSTOMER_NOT_FOUND);
    }

    @ExceptionHandler(CustomerAnonymizedException.class)
    ResponseEntity<ProblemDetail> handleAnonymized(CustomerAnonymizedException ex) {
        return ApiProblems.of(HttpStatus.CONFLICT, ex.getMessage(), ApiErrorCode.CUSTOMER_ANONYMIZED);
    }

    @ExceptionHandler(CustomerClaimedException.class)
    ResponseEntity<ProblemDetail> handleClaimed(CustomerClaimedException ex) {
        return ApiProblems.of(HttpStatus.CONFLICT, ex.getMessage(), ApiErrorCode.CUSTOMER_CLAIMED);
    }

    @ExceptionHandler(CustomerSharedException.class)
    ResponseEntity<ProblemDetail> handleShared(CustomerSharedException ex) {
        return ApiProblems.of(HttpStatus.CONFLICT, ex.getMessage(), ApiErrorCode.CUSTOMER_SHARED);
    }
}



