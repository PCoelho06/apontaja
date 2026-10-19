package com.apontaja.back.customer.application;

import java.util.UUID;

/** Remplacement complet des champs modifiables d'un client (PUT). */
public record UpdateCustomerCommand(UUID salonId, UUID customerProfileId, String firstName, String lastName,
        String email, String phone, String internalNotes) {
}
