package com.apontaja.back.customer.application;

import java.util.List;
import java.util.UUID;

public record MergeCustomersCommand(UUID salonId, UUID survivorId, List<UUID> absorbedIds) {
}
