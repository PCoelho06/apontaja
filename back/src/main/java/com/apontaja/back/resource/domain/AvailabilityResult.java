package com.apontaja.back.resource.domain;

import java.util.List;

public record AvailabilityResult(List<AvailabilityViolation> violations) {

    public AvailabilityResult {
        violations = List.copyOf(violations);
    }

    public boolean isAvailable() {
        return violations.isEmpty();
    }
}
