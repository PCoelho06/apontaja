package com.apontaja.back.resource.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeIntervalTest {

    private static Instant at(int hour) {
        return Instant.parse("2026-09-24T00:00:00Z").plusSeconds(hour * 3600L);
    }

    private static TimeInterval interval(int fromHour, int toHour) {
        return new TimeInterval(at(fromHour), at(toHour));
    }

    @Test
    void detecte_un_chevauchement_partiel_dans_les_deux_sens() {
        assertThat(interval(10, 12).overlaps(interval(11, 13))).isTrue();
        assertThat(interval(11, 13).overlaps(interval(10, 12))).isTrue();
    }

    @Test
    void detecte_l_inclusion_la_contenance_et_l_identite() {
        assertThat(interval(10, 14).overlaps(interval(11, 12))).isTrue();
        assertThat(interval(11, 12).overlaps(interval(10, 14))).isTrue();
        assertThat(interval(10, 12).overlaps(interval(10, 12))).isTrue();
    }

    @Test
    void des_intervalles_adjacents_ou_disjoints_ne_se_chevauchent_pas() {
        assertThat(interval(10, 12).overlaps(interval(12, 14))).isFalse();
        assertThat(interval(12, 14).overlaps(interval(10, 12))).isFalse();
        assertThat(interval(10, 11).overlaps(interval(13, 14))).isFalse();
    }

    @Test
    void contains_inclut_les_bornes_mais_refuse_un_depassement_meme_d_une_seconde() {
        assertThat(interval(9, 18).contains(interval(9, 18))).isTrue();
        assertThat(interval(9, 18).contains(interval(10, 11))).isTrue();
        assertThat(interval(9, 18).contains(new TimeInterval(at(9).minusSeconds(1), at(10)))).isFalse();
        assertThat(interval(9, 18).contains(new TimeInterval(at(17), at(18).plusSeconds(1)))).isFalse();
    }

    @Test
    void refuse_une_fin_non_posterieure_au_debut() {
        assertThatThrownBy(() -> new TimeInterval(at(10), at(10))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TimeInterval(at(11), at(10))).isInstanceOf(IllegalArgumentException.class);
    }
}
