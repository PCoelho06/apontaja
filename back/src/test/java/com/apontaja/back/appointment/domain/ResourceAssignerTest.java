package com.apontaja.back.appointment.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceAssignerTest {

    private static final UUID R1 = new UUID(0, 1);
    private static final UUID R2 = new UUID(0, 2);
    private static final Instant DAY_START = Instant.parse("2099-06-09T22:00:00Z");
    private static final Instant DAY_END = Instant.parse("2099-06-10T22:00:00Z");
    private static final Instant OLD = Instant.parse("2020-01-01T00:00:00Z");
    private static final Instant NEWER = Instant.parse("2021-01-01T00:00:00Z");

    private static BusyInterval busy(UUID resourceId, String start, String end) {
        return new BusyInterval(resourceId, Instant.parse(start), Instant.parse(end));
    }

    private static UUID choose(List<ResourceAssigner.Candidate> candidates, List<BusyInterval> busy) {
        return ResourceAssigner.choose(candidates, busy, DAY_START, DAY_END);
    }

    @Test
    void choisit_la_ressource_la_moins_chargee_du_jour() {
        List<ResourceAssigner.Candidate> candidates = List.of(new ResourceAssigner.Candidate(R1, OLD),
                new ResourceAssigner.Candidate(R2, NEWER));
        List<BusyInterval> busy = List.of(busy(R1, "2099-06-10T07:00:00Z", "2099-06-10T08:00:00Z"),
                busy(R2, "2099-06-10T07:00:00Z", "2099-06-10T07:30:00Z"));

        assertThat(choose(candidates, busy)).isEqualTo(R2);
    }

    @Test
    void a_charge_egale_prend_la_plus_ancienne() {
        List<ResourceAssigner.Candidate> candidates = List.of(new ResourceAssigner.Candidate(R1, NEWER),
                new ResourceAssigner.Candidate(R2, OLD));

        assertThat(choose(candidates, List.of())).isEqualTo(R2);
    }

    @Test
    void a_anciennete_egale_prend_le_plus_petit_identifiant() {
        List<ResourceAssigner.Candidate> candidates = List.of(new ResourceAssigner.Candidate(R2, OLD),
                new ResourceAssigner.Candidate(R1, OLD));

        assertThat(choose(candidates, List.of())).isEqualTo(R1);
    }

    @Test
    void ne_compte_que_la_part_du_rendez_vous_qui_recouvre_le_jour() {
        List<ResourceAssigner.Candidate> candidates = List.of(new ResourceAssigner.Candidate(R1, OLD),
                new ResourceAssigner.Candidate(R2, NEWER));
        // R1 : 10 minutes dans le jour (le reste est la veille) ; R2 : 30 minutes.
        List<BusyInterval> busy = List.of(busy(R1, "2099-06-09T21:00:00Z", "2099-06-09T22:10:00Z"),
                busy(R2, "2099-06-10T07:00:00Z", "2099-06-10T07:30:00Z"));

        assertThat(choose(candidates, busy)).isEqualTo(R1);
    }

    @Test
    void ignore_les_plages_d_une_ressource_hors_candidats() {
        UUID other = new UUID(0, 9);
        List<ResourceAssigner.Candidate> candidates = List.of(new ResourceAssigner.Candidate(R1, NEWER),
                new ResourceAssigner.Candidate(R2, OLD));

        assertThat(choose(candidates, List.of(busy(other, "2099-06-10T07:00:00Z", "2099-06-10T09:00:00Z"))))
                .isEqualTo(R2);
    }

    @Test
    void refuse_une_liste_de_candidats_vide() {
        assertThatThrownBy(() -> choose(List.of(), List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
