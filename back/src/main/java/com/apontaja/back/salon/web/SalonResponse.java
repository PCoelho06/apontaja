package com.apontaja.back.salon.web;

import java.util.UUID;

public record SalonResponse(UUID salonId, UUID organizationId, String name, String address, String postalCode,
        String city, String country, String phone, String timezone) {
}
