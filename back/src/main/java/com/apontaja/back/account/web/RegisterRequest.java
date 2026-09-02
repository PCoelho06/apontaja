package com.apontaja.back.account.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,

        // Longueur minimale seule (12+), pas de règles de composition —
        // approche OWASP actuelle plutôt que la regex fragile de l'ancien
        // projet. Max 128 pour éviter un mot de passe absurdement long
        // (coût de hash), pas une limite bcrypt (on est sur Argon2id).
        @NotBlank
        @Size(min = 12, max = 128, message = "Le mot de passe doit contenir au moins 12 caractères")
        String password) {
}
