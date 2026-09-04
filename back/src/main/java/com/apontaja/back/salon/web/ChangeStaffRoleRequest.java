package com.apontaja.back.salon.web;

import jakarta.validation.constraints.NotBlank;

public record ChangeStaffRoleRequest(@NotBlank String role) {
}
