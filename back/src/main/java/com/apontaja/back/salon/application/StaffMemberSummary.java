package com.apontaja.back.salon.application;

import java.time.Instant;
import java.util.UUID;

public record StaffMemberSummary(UUID staffMembershipId, UUID accountId, String role, Instant since) {
}
