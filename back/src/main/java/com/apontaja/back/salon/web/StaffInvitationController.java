package com.apontaja.back.salon.web;

import com.apontaja.back.salon.application.AccountAlreadyStaffMemberException;
import com.apontaja.back.salon.application.CreateStaffInvitationCommand;
import com.apontaja.back.salon.application.CreateStaffInvitationResult;
import com.apontaja.back.salon.application.InvalidStaffRoleException;
import com.apontaja.back.salon.application.StaffInvitationAlreadyPendingException;
import com.apontaja.back.salon.application.StaffInvitationService;
import com.apontaja.back.salon.application.StaffInvitationSummary;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

@RestController
@RequestMapping("/api/salons/{salonId}/staff/invitations")
class StaffInvitationController {

    private final StaffInvitationService staffInvitationService;

    StaffInvitationController(StaffInvitationService staffInvitationService) {
        this.staffInvitationService = staffInvitationService;
    }

    @PostMapping
    @PreAuthorize("@staffManagementGuard.canManageRole(authentication.principal, #salonId, #request.role())")
    public ResponseEntity<CreateStaffInvitationResponse> createInvitation(@P("salonId") @PathVariable UUID salonId,
            @P("request") @Valid @RequestBody CreateStaffInvitationRequest request,
            @AuthenticationPrincipal UUID accountId) {

        CreateStaffInvitationResult result = staffInvitationService.createInvitation(
                new CreateStaffInvitationCommand(salonId, accountId, request.email(), request.role()));

        return ResponseEntity.status(HttpStatus.CREATED).body(new CreateStaffInvitationResponse(result.invitationId()));
    }

    @GetMapping
    @PreAuthorize("@salonAccessGuard.hasAccessToSalon(authentication.principal, #salonId)")
    public ResponseEntity<List<StaffInvitationResponse>> listPending(@P("salonId") @PathVariable UUID salonId) {
        List<StaffInvitationResponse> body = staffInvitationService.listPending(salonId).stream()
                .map(StaffInvitationController::toResponse).toList();
        return ResponseEntity.ok(body);
    }

    @ExceptionHandler(StaffInvitationAlreadyPendingException.class)
    ResponseEntity<ProblemDetail> handleAlreadyPending(StaffInvitationAlreadyPendingException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage()));
    }

    @ExceptionHandler(AccountAlreadyStaffMemberException.class)
    ResponseEntity<ProblemDetail> handleAlreadyMember(AccountAlreadyStaffMemberException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage()));
    }

    @ExceptionHandler(InvalidStaffRoleException.class)
    ResponseEntity<ProblemDetail> handleInvalidRole(InvalidStaffRoleException ex) {
        return ResponseEntity.badRequest()
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    private static StaffInvitationResponse toResponse(StaffInvitationSummary summary) {
        return new StaffInvitationResponse(summary.invitationId(), summary.email(), summary.role(), summary.createdAt(),
                summary.expiresAt());
    }
}
