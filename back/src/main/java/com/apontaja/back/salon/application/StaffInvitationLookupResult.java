package com.apontaja.back.salon.application;

import java.time.Instant;
import java.util.UUID;

public record StaffInvitationLookupResult(UUID invitationId, String email, String role, String salonName,
        boolean accountExists, Instant expiresAt) {
}
