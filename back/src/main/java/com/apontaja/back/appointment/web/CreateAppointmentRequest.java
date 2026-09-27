package com.apontaja.back.appointment.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** startAt : ISO 8601 avec offset (ex. 2026-10-01T09:00:00+02:00). endAt n'est jamais fourni
 * par le client : calculé côté serveur à partir de la durée effective (snapshot). */
public record CreateAppointmentRequest(@NotNull UUID customerProfileId, @NotNull UUID serviceId,
        @NotNull UUID resourceId, @NotBlank String startAt) {
}
