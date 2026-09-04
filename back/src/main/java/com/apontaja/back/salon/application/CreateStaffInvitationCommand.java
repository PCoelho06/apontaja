package com.apontaja.back.salon.application;

import java.util.UUID;

public record CreateStaffInvitationCommand(UUID salonId, UUID invitedBy, String email, String role) {
}
