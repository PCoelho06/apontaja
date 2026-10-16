package com.apontaja.back.salon.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Membre du staff avec l'email de son compte (null si l'acteur n'a pas le droit
 * de le voir ou si le compte n'est plus vivant).
 */
public record StaffMemberDetails(UUID staffMembershipId, UUID accountId, String displayName, String email, String role,
        Instant since) {
}
