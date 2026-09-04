package com.apontaja.back.salon.web;

import com.apontaja.back.salon.application.CreateSalonCommand;
import com.apontaja.back.salon.application.CreateSalonResult;
import com.apontaja.back.salon.application.SalonCreationService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/salons")
class SalonController {

    private final SalonCreationService salonCreationService;

    SalonController(SalonCreationService salonCreationService) {
        this.salonCreationService = salonCreationService;
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
}
