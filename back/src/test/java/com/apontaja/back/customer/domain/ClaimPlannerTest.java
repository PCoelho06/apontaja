package com.apontaja.back.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

class ClaimPlannerTest {

    private static final UUID SALON_A = new UUID(0, 1);
    private static final UUID SALON_B = new UUID(0, 2);
    private static final UUID SALON_C = new UUID(0, 3);
    private static final Instant T0 = Instant.parse("2020-01-01T10:00:00Z");

    private static ClaimCandidate candidate(long profile, UUID salon, long daysAfter) {
        return new ClaimCandidate(new UUID(1, profile), salon, T0.plusSeconds(daysAfter * 86_400));
    }

    @Test
    void garde_le_profil_le_plus_ancien_de_chaque_salon() {
        ClaimCandidate recentA = candidate(1, SALON_A, 5);
        ClaimCandidate oldA = candidate(2, SALON_A, 1);
        ClaimCandidate b = candidate(3, SALON_B, 2);

        List<ClaimCandidate> plan = ClaimPlanner.plan(List.of(recentA, oldA, b), Set.of());

        assertThat(plan).containsExactly(oldA, b);
    }

    @Test
    void departage_deux_profils_crees_au_meme_instant_par_leur_identifiant() {
        ClaimCandidate first = candidate(1, SALON_A, 0);
        ClaimCandidate second = candidate(2, SALON_A, 0);

        assertThat(ClaimPlanner.plan(List.of(second, first), Set.of())).containsExactly(first);
    }

    @Test
    void ignore_un_profil_lie_a_plusieurs_salons() {
        UUID sharedProfile = new UUID(1, 9);
        ClaimCandidate inA = new ClaimCandidate(sharedProfile, SALON_A, T0);
        ClaimCandidate inB = new ClaimCandidate(sharedProfile, SALON_B, T0);
        ClaimCandidate alone = candidate(4, SALON_C, 3);

        assertThat(ClaimPlanner.plan(List.of(inA, inB, alone), Set.of())).containsExactly(alone);
    }

    @Test
    void ignore_les_salons_ou_le_compte_a_deja_un_profil_reclame() {
        ClaimCandidate a = candidate(1, SALON_A, 0);
        ClaimCandidate b = candidate(2, SALON_B, 0);

        assertThat(ClaimPlanner.plan(List.of(a, b), Set.of(SALON_A))).containsExactly(b);
    }

    @Test
    void trie_le_resultat_par_salon_et_accepte_une_liste_vide() {
        ClaimCandidate c = candidate(1, SALON_C, 0);
        ClaimCandidate a = candidate(2, SALON_A, 0);

        assertThat(ClaimPlanner.plan(List.of(c, a), Set.of())).containsExactly(a, c);
        assertThat(ClaimPlanner.plan(List.of(), Set.of())).isEmpty();
    }
}
