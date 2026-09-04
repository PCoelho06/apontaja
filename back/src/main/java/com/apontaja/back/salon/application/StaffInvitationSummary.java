package com.apontaja.back.salon.application;

import java.time.Instant;
import java.util.UUID;

public record StaffInvitationSummary(UUID invitationId, String email, String role, Instant createdAt,
        Instant expiresAt) {
}
