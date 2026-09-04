package com.apontaja.back.salon.application;

import java.util.UUID;

public record SalonSummary(UUID salonId, UUID organizationId, String name, String address, String postalCode,
        String city, String country, String phone, String timezone) {
}
