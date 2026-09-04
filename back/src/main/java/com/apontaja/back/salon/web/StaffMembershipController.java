package com.apontaja.back.salon.web;

import com.apontaja.back.salon.application.InvalidStaffRoleException;
import com.apontaja.back.salon.application.LastOwnerProtectionException;
import com.apontaja.back.salon.application.StaffMemberSummary;
import com.apontaja.back.salon.application.StaffMembershipManagementService;
import com.apontaja.back.salon.application.StaffMembershipNotFoundException;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/salons/{salonId}/staff")
class StaffMembershipController {

    private final StaffMembershipManagementService staffMembershipManagementService;

    StaffMembershipController(StaffMembershipManagementService staffMembershipManagementService) {
        this.staffMembershipManagementService = staffMembershipManagementService;
    }

    @GetMapping
    @PreAuthorize("@salonAccessGuard.hasAccessToSalon(authentication.principal, #salonId)")
    public ResponseEntity<List<StaffMemberResponse>> listMembers(@P("salonId") @PathVariable UUID salonId) {
        List<StaffMemberResponse> body = staffMembershipManagementService.listMembers(salonId).stream()
                .map(StaffMembershipController::toResponse).toList();
        return ResponseEntity.ok(body);
    }

    @PatchMapping("/{staffMembershipId}")
    @PreAuthorize("@staffManagementGuard.canChangeStaffRole("
            + "authentication.principal, #salonId, #staffMembershipId, #request.role())")
    public ResponseEntity<Void> changeRole(@P("salonId") @PathVariable UUID salonId,
            @P("staffMembershipId") @PathVariable UUID staffMembershipId,
            @P("request") @Valid @RequestBody ChangeStaffRoleRequest request) {
        staffMembershipManagementService.changeRole(salonId, staffMembershipId, request.role());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{staffMembershipId}")
    @PreAuthorize("@staffManagementGuard.canManageMembership(authentication.principal, #salonId, #staffMembershipId)")
    public ResponseEntity<Void> removeMember(@P("salonId") @PathVariable UUID salonId,
            @P("staffMembershipId") @PathVariable UUID staffMembershipId) {
        staffMembershipManagementService.removeMember(salonId, staffMembershipId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(LastOwnerProtectionException.class)
    ResponseEntity<ProblemDetail> handleLastOwner(LastOwnerProtectionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage()));
    }

    @ExceptionHandler(StaffMembershipNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(StaffMembershipNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(InvalidStaffRoleException.class)
    ResponseEntity<ProblemDetail> handleInvalidRole(InvalidStaffRoleException ex) {
        return ResponseEntity.badRequest()
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    private static StaffMemberResponse toResponse(StaffMemberSummary summary) {
        return new StaffMemberResponse(summary.staffMembershipId(), summary.accountId(), summary.role(),
                summary.since());
    }
}
