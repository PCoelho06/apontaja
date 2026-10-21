package com.apontaja.back.account.web;

import com.apontaja.back.account.application.FrontendPortal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(@NotBlank @Email String email,

                @NotBlank @Size(max = 100) String firstName,

                @NotBlank @Size(max = 100) String lastName,

                // Longueur minimale seule (12+), pas de règles de composition —
                // approche OWASP actuelle plutôt que la regex fragile de l'ancien
                // projet. Max 128 pour éviter un mot de passe absurdement long
                // (coût de hash), pas une limite bcrypt (on est sur Argon2id).
                @NotBlank @Size(min = 12, max = 128,
                                message = "Le mot de passe doit contenir au moins 12 caractères") String password,

                // Acceptation explicite (cases à cocher), enregistrée en consentements TOS et PRIVACY.
                // Primitif : un champ absent vaut false (un Boolean nul passerait @AssertTrue).
                @AssertTrue(message = "Vous devez accepter les conditions d'utilisation") boolean acceptTos,

                @AssertTrue(message = "Vous devez accepter la politique de confidentialité") boolean acceptPrivacy,

                // Portail d'origine, pour les liens des emails ; absent = portail salon.
                FrontendPortal portal) {
}
