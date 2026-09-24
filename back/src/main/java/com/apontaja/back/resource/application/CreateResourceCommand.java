package com.apontaja.back.resource.application;

import java.util.UUID;

public record CreateResourceCommand(UUID salonId, String name, String type) {
}
