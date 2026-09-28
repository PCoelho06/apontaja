package com.apontaja.back.appointment.web;

import jakarta.validation.constraints.NotBlank;

record RescheduleAppointmentRequest(@NotBlank String startAt) {
}
