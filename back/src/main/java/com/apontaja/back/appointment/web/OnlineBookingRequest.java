package com.apontaja.back.appointment.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * resourceId nul = « sans préférence » ; startAt : ISO 8601 avec offset. shareContactConsent est un {@code Boolean}
 * : absent = refus (le service ne l'exige que pour une première réservation dans le salon).
 */
public record OnlineBookingRequest(@NotNull UUID serviceId, UUID resourceId, @NotBlank String startAt,
        @Size(max = 30) String phone, Boolean shareContactConsent) {
}
