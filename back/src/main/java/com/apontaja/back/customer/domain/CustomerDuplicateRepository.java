package com.apontaja.back.customer.domain;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Lectures pour la détection de doublons d'un salon. */
public interface CustomerDuplicateRepository {

    /** Profils vivants et non anonymisés ayant un lien vivant dans ce salon. */
    List<DuplicateCandidate> findCandidates(UUID salonId);

    /** Parmi {@code profileIds}, ceux qui ont aussi un lien vivant dans un autre salon. */
    Set<UUID> findProfileIdsLinkedElsewhere(UUID salonId, Collection<UUID> profileIds);
}
