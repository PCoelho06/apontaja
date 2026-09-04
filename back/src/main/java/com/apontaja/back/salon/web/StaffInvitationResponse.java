package com.apontaja.back.salon.web;

import java.time.Instant;
import java.util.UUID;

public record StaffInvitationResponse(UUID invitationId, String email, String role, Instant createdAt,
        Instant expiresAt) {
}
