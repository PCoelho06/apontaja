package com.apontaja.back.customer.application;

import java.util.UUID;

public record CreateCustomerCommand(UUID salonId, String firstName, String lastName, String email, String phone,
        String internalNotes) {
}
