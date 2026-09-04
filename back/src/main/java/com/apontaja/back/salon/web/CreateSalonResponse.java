package com.apontaja.back.salon.web;

import java.util.UUID;

public record CreateSalonResponse(UUID salonId, UUID organizationId) {
}
