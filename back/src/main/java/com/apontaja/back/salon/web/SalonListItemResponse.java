package com.apontaja.back.salon.web;

import com.apontaja.back.salon.application.SalonAccessRole;

import java.util.UUID;

public record SalonListItemResponse(UUID salonId, UUID organizationId, String name, String address, String postalCode,
        String city, String country, String phone, String timezone, SalonAccessRole role) {
}
