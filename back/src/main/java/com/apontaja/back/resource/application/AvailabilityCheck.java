package com.apontaja.back.resource.application;

import java.util.List;

public record AvailabilityCheck(boolean available, List<AvailabilityIssue> issues) {
}
