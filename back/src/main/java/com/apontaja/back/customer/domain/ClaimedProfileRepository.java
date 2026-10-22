package com.apontaja.back.customer.domain;

import java.util.Optional;
import java.util.UUID;

public interface ClaimedProfileRepository {

    /**
     * Profil vivant et non anonymisé que le compte a réclamé dans ce salon (le plus ancien s'il y en avait
     * plusieurs, ce que {@code CustomerClaimService} évite).
     */
    Optional<UUID> findClaimedProfileId(UUID accountId, UUID salonId);
}
