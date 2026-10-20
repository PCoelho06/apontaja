package com.apontaja.back.appointment.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlotGridTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final Instant EARLY = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant LATE = Instant.parse("2200-01-01T00:00:00Z");

    private static List<Instant> starts(String first, String last, int granularity) {
        return SlotGrid.candidateStarts(PARIS, LocalDate.parse(first), LocalDate.parse(last), granularity, EARLY,
                LATE);
    }

    @Test
    void un_jour_normal_compte_96_debuts_a_15_minutes_ancres_a_minuit_local() {
        List<Instant> starts = starts("2099-06-10", "2099-06-10", 15);

        assertThat(starts).hasSize(96);
        assertThat(starts.get(0)).isEqualTo(Instant.parse("2099-06-09T22:00:00Z"));
        assertThat(starts.get(95)).isEqualTo(Instant.parse("2099-06-10T21:45:00Z"));
    }

    @Test
    void le_saut_de_printemps_supprime_l_heure_inexistante() {
        // 2099-03-29 : 02:00 -> 03:00 en Europe/Paris, journée de 23 h.
        List<Instant> starts = starts("2099-03-29", "2099-03-29", 15);

        assertThat(starts).hasSize(92);
        assertThat(starts).noneMatch(start -> start.atZone(PARIS).getHour() == 2);
    }

    @Test
    void le_retour_d_automne_garde_les_deux_occurrences_de_l_heure_ambigue() {
        // 2099-10-25 : 03:00 -> 02:00 en Europe/Paris, journée de 25 h.
        List<Instant> starts = starts("2099-10-25", "2099-10-25", 15);

        assertThat(starts).hasSize(100);
        assertThat(starts.stream().filter(start -> start.atZone(PARIS).getHour() == 2)).hasSize(8);
    }

    @Test
    void la_granularite_change_le_pas_et_plusieurs_jours_s_enchainent() {
        assertThat(starts("2099-06-10", "2099-06-10", 30)).hasSize(48);
        assertThat(starts("2099-06-10", "2099-06-10", 60)).hasSize(24);
        assertThat(starts("2099-06-10", "2099-06-12", 15)).hasSize(288);
    }

    @Test
    void borne_les_debuts_par_le_plus_tot_inclus_et_le_plus_tard_exclu() {
        Instant earliest = Instant.parse("2099-06-10T08:00:00Z");
        Instant latest = Instant.parse("2099-06-10T09:00:00Z");

        List<Instant> starts = SlotGrid.candidateStarts(PARIS, LocalDate.parse("2099-06-10"),
                LocalDate.parse("2099-06-10"), 15, earliest, latest);

        assertThat(starts).containsExactly(Instant.parse("2099-06-10T08:00:00Z"),
                Instant.parse("2099-06-10T08:15:00Z"), Instant.parse("2099-06-10T08:30:00Z"),
                Instant.parse("2099-06-10T08:45:00Z"));
    }

    @Test
    void aucun_debut_si_la_fenetre_est_entierement_hors_bornes() {
        Instant latest = Instant.parse("2099-06-01T00:00:00Z");

        assertThat(SlotGrid.candidateStarts(PARIS, LocalDate.parse("2099-06-10"), LocalDate.parse("2099-06-10"),
                15, EARLY, latest)).isEmpty();
        assertThat(SlotGrid.candidateStarts(PARIS, LocalDate.parse("2099-06-10"), LocalDate.parse("2099-06-10"),
                15, Instant.parse("2099-07-01T00:00:00Z"), LATE)).isEmpty();
    }

    @Test
    void refuse_une_granularite_qui_ne_divise_pas_60_ou_une_periode_inversee() {
        assertThatThrownBy(() -> starts("2099-06-10", "2099-06-10", 7)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> starts("2099-06-10", "2099-06-10", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> starts("2099-06-11", "2099-06-10", 15))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isFree_traite_les_bornes_adjacentes_comme_libres() {
        UUID resource = UUID.randomUUID();
        List<BusyInterval> busy = List.of(new BusyInterval(resource, Instant.parse("2099-06-10T08:00:00Z"),
                Instant.parse("2099-06-10T09:00:00Z")));

        assertThat(SlotGrid.isFree(Instant.parse("2099-06-10T07:00:00Z"), Instant.parse("2099-06-10T08:00:00Z"),
                busy)).isTrue();
        assertThat(SlotGrid.isFree(Instant.parse("2099-06-10T09:00:00Z"), Instant.parse("2099-06-10T10:00:00Z"),
                busy)).isTrue();
        assertThat(SlotGrid.isFree(Instant.parse("2099-06-10T07:30:00Z"), Instant.parse("2099-06-10T08:30:00Z"),
                busy)).isFalse();
        assertThat(SlotGrid.isFree(Instant.parse("2099-06-10T08:15:00Z"), Instant.parse("2099-06-10T08:45:00Z"),
                busy)).isFalse();
        assertThat(SlotGrid.isFree(Instant.parse("2099-06-10T07:00:00Z"), Instant.parse("2099-06-10T08:00:00Z"),
                List.of())).isTrue();
    }
}
