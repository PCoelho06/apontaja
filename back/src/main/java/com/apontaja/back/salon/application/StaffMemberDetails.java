package com.apontaja.back.salon.application;

import java.time.Instant;
import java.util.UUID;

/** Membre du staff avec l'email de son compte (null si le compte n'est plus vivant). */
public record StaffMemberDetails(UUID staffMembershipId, UUID accountId, String email, String role, Instant since) {
}
