package com.apontaja.back.customer.domain;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Choisit les profils qu'un compte peut réclamer. Règles : un profil lié à plusieurs salons n'est jamais réclamé ;
 * au plus un profil réclamé par salon (le plus ancien) ; un salon où le compte a déjà un profil réclamé est
 * ignoré. Le résultat est trié par salon (ordre de verrouillage stable).
 */
public final class ClaimPlanner {

    private static final Comparator<ClaimCandidate> OLDEST_FIRST = Comparator.comparing(ClaimCandidate::createdAt)
            .thenComparing(ClaimCandidate::profileId);

    private ClaimPlanner() {
    }

    public static List<ClaimCandidate> plan(List<ClaimCandidate> candidates, Set<UUID> salonsAlreadyClaimed) {
        Map<UUID, Set<UUID>> salonsByProfile = new HashMap<>();
        for (ClaimCandidate candidate : candidates) {
            salonsByProfile.computeIfAbsent(candidate.profileId(), id -> new HashSet<>()).add(candidate.salonId());
        }

        Map<UUID, ClaimCandidate> oldestBySalon = new HashMap<>();
        for (ClaimCandidate candidate : candidates) {
            boolean shared = salonsByProfile.get(candidate.profileId()).size() > 1;
            if (shared || salonsAlreadyClaimed.contains(candidate.salonId())) {
                continue;
            }
            oldestBySalon.merge(candidate.salonId(), candidate,
                    (current, other) -> OLDEST_FIRST.compare(current, other) <= 0 ? current : other);
        }
        return oldestBySalon.values().stream().sorted(Comparator.comparing(ClaimCandidate::salonId)).toList();
    }
}
