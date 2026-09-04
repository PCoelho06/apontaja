package com.apontaja.back.salon.application;

import java.util.UUID;

public record CreateSalonCommand(UUID accountId, String name, String address, String postalCode, String city,
        String country, String timezone, String phone) {
}
