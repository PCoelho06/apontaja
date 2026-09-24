package com.apontaja.back.resource.web;

import java.time.Instant;
import java.util.UUID;

public record ResourceResponse(UUID resourceId, UUID salonId, String name, String type, Instant createdAt) {
}
