package com.apontaja.back.salon.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateStaffInvitationRequest(@NotBlank @Email String email, @NotBlank String role) {
}
