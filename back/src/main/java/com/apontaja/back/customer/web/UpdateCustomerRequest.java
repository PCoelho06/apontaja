package com.apontaja.back.customer.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Remplacement complet (PUT). Au moins un email ou un téléphone : contrôlé par le service applicatif (400). */
public record UpdateCustomerRequest(@NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName, @Email @Size(max = 254) String email,
        @Size(max = 30) String phone, @Size(max = 2000) String internalNotes) {
}
