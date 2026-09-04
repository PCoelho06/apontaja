package com.apontaja.back.salon.web;

import com.apontaja.back.salon.application.CreateSalonCommand;
import com.apontaja.back.salon.application.CreateSalonResult;
import com.apontaja.back.salon.application.SalonCreationService;
import com.apontaja.back.salon.application.SalonQueryService;
import com.apontaja.back.salon.application.SalonSummary;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/salons")
class SalonController {

    private final SalonCreationService salonCreationService;
    private final SalonQueryService salonQueryService;

    SalonController(SalonCreationService salonCreationService, SalonQueryService salonQueryService) {
        this.salonCreationService = salonCreationService;
        this.salonQueryService = salonQueryService;
    }

    @PostMapping
    ResponseEntity<CreateSalonResponse> createSalon(@Valid @RequestBody CreateSalonRequest request,
            @AuthenticationPrincipal UUID accountId) {

        CreateSalonResult result = salonCreationService
                .createSalon(new CreateSalonCommand(accountId, request.name(), request.address(), request.postalCode(),
                        request.city(), request.country(), request.timezone(), request.phone()));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateSalonResponse(result.salonId(), result.organizationId()));
    }

    /**
     * Premier endpoint du domaine salon avec RBAC réel (Phase 2, tranche 3) : accès
     * autorisé si StaffMembership actif sur ce salon OU OrganizationMembership
     * OWNER sur l'organisation propriétaire — voir SalonAccessGuard. 403 uniforme
     * (jamais 404) que le salon n'existe pas ou que l'accès soit refusé, pour ne
     * pas confirmer son existence.
     */
    @GetMapping("/{salonId}")
    @PreAuthorize("@salonAccessGuard.hasAccessToSalon(authentication.principal, #salonId)")
    public ResponseEntity<SalonResponse> getSalon(@P("salonId") @PathVariable UUID salonId) {
        return salonQueryService.findAliveById(salonId).map(SalonController::toResponse).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    private static SalonResponse toResponse(SalonSummary summary) {
        return new SalonResponse(summary.salonId(), summary.organizationId(), summary.name(), summary.address(),
                summary.postalCode(), summary.city(), summary.country(), summary.phone(), summary.timezone());
    }
}
