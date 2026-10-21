package com.apontaja.back.customer.domain;

import java.time.Instant;
import java.util.UUID;

/** Profil non réclamé dont l'email correspond, avec le salon de son lien (une ligne par lien vivant). */
public record ClaimCandidate(UUID profileId, UUID salonId, Instant createdAt) {
}
