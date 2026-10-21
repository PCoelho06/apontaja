package com.apontaja.back.customer.domain;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Lectures nécessaires au rattachement d'un compte à des profils de carnets de salons. */
public interface CustomerClaimRepository {

    /**
     * Profils vivants, non anonymisés et non réclamés dont l'email est égal (sans tenir compte de la casse) à
     * {@code email}, une ligne par lien vivant, du plus ancien au plus récent.
     */
    List<ClaimCandidate> findClaimCandidates(String email);

    /** Salons où le compte possède déjà un profil réclamé vivant. */
    Set<UUID> findSalonIdsHoldingClaimedProfile(UUID accountId);
}
