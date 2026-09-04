package com.apontaja.back.salon.application;

import java.util.UUID;

public record CreateSalonResult(UUID salonId, UUID organizationId) {
}
