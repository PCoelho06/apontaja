package com.apontaja.back.customer.web;

import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(UUID customerProfileId, UUID salonCustomerLinkId, UUID salonId, String firstName,
        String lastName, String email, String phone, String internalNotes, Instant createdAt) {
}
