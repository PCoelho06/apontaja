package com.apontaja.back.salon.web;

import java.time.Instant;
import java.util.UUID;

public record StaffMemberResponse(UUID staffMembershipId, UUID accountId, String role, Instant since) {
}
