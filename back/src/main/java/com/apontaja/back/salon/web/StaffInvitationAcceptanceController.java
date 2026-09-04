package com.apontaja.back.salon.web;

import com.apontaja.back.salon.application.InvalidOrExpiredInvitationException;
import com.apontaja.back.salon.application.InvitationEmailMismatchException;
import com.apontaja.back.salon.application.StaffInvitationLookupResult;
import com.apontaja.back.salon.application.StaffInvitationService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/staff-invitations")
class StaffInvitationAcceptanceController {

    private final StaffInvitationService staffInvitationService;

    StaffInvitationAcceptanceController(StaffInvitationService staffInvitationService) {
        this.staffInvitationService = staffInvitationService;
    }

    /**
     * Public, non authentifié — voir la note sur le rate limiting en tête de
     * réponse.
     */
    @GetMapping("/{token}")
    ResponseEntity<StaffInvitationLookupResponse> lookup(@PathVariable String token) {
        StaffInvitationLookupResult result = staffInvitationService.lookup(token);
        return ResponseEntity.ok(new StaffInvitationLookupResponse(result.invitationId(), result.email(), result.role(),
                result.salonName(), result.accountExists(), result.expiresAt()));
    }

    @PostMapping("/accept")
    ResponseEntity<Void> accept(@Valid @RequestBody AcceptStaffInvitationRequest request,
            @AuthenticationPrincipal UUID accountId) {
        staffInvitationService.accept(accountId, request.token());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(InvalidOrExpiredInvitationException.class)
    ResponseEntity<ProblemDetail> handleInvalid(InvalidOrExpiredInvitationException ex) {
        return ResponseEntity.badRequest()
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(InvitationEmailMismatchException.class)
    ResponseEntity<ProblemDetail> handleMismatch(InvitationEmailMismatchException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage()));
    }
}
