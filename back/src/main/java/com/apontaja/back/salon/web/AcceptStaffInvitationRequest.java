package com.apontaja.back.salon.web;

import jakarta.validation.constraints.NotBlank;

public record AcceptStaffInvitationRequest(@NotBlank String token) {
}
