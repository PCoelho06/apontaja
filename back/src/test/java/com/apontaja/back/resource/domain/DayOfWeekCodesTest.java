package com.apontaja.back.resource.domain;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DayOfWeekCodesTest {

    @Test
    void le_dimanche_vaut_0_et_le_lundi_1_comme_dans_le_schema() {
        assertThat(DayOfWeekCodes.toCode(DayOfWeek.SUNDAY)).isZero();
        assertThat(DayOfWeekCodes.toCode(DayOfWeek.MONDAY)).isEqualTo((short) 1);
        assertThat(DayOfWeekCodes.toCode(DayOfWeek.SATURDAY)).isEqualTo((short) 6);
    }

    @Test
    void aller_retour_exhaustif_sur_les_sept_jours() {
        for (DayOfWeek day : DayOfWeek.values()) {
            assertThat(DayOfWeekCodes.fromCode(DayOfWeekCodes.toCode(day))).isEqualTo(day);
        }
    }

    @Test
    void les_codes_produits_couvrent_exactement_0_a_6() {
        long distinct = java.util.Arrays.stream(DayOfWeek.values()).map(DayOfWeekCodes::toCode).distinct().count();
        assertThat(distinct).isEqualTo(7);
    }

    @Test
    void rejette_un_code_hors_intervalle() {
        assertThatThrownBy(() -> DayOfWeekCodes.fromCode(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DayOfWeekCodes.fromCode(7)).isInstanceOf(IllegalArgumentException.class);
    }
}
