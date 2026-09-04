package com.apontaja.back.salon.web;

import java.time.Instant;
import java.util.UUID;

public record StaffInvitationLookupResponse(UUID invitationId, String email, String role, String salonName,
        boolean accountExists, Instant expiresAt) {
}
