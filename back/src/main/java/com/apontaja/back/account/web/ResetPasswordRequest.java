package com.apontaja.back.account.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank String token,

        @NotBlank
        @Size(min = 12, max = 128, message = "Le mot de passe doit contenir au moins 12 caractères")
        String newPassword) {
}
