package com.apontaja.back.customer.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** email et phone sont tous deux optionnels côté validation : au moins l'un des deux est
 * exigé par le service applicatif (ContactRequiredException, 400) — validation croisée non
 * exprimable proprement avec de simples annotations de champ. */
public record CreateCustomerRequest(@NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName, @Email @Size(max = 254) String email,
        @Size(max = 30) String phone, @Size(max = 2000) String internalNotes) {
}
