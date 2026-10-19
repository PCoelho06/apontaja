package com.apontaja.back.customer.domain;

import java.time.Instant;
import java.util.UUID;

/** Profil vivant et non anonymisé d'un salon, vu par la détection de doublons. */
public record DuplicateCandidate(UUID profileId, String firstName, String lastName, String email, String phone,
        UUID accountId, Instant createdAt) {
}
