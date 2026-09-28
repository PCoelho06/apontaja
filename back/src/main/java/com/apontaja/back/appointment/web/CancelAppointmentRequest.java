package com.apontaja.back.appointment.web;

import jakarta.validation.constraints.Size;

/** reason optionnel : le corps doit toujours être envoyé (au moins {}). */
public record CancelAppointmentRequest(@Size(max = 500) String reason) {
}
