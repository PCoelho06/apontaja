package com.apontaja.back.account.application;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccountSummaryTest {

    private static String displayName(String firstName, String lastName) {
        return new AccountSummary(UUID.randomUUID(), "a@example.com", true, firstName, lastName).displayName();
    }

    @Test
    void affiche_le_prenom_et_l_initiale_du_nom() {
        assertThat(displayName("Pierre", "Dupont")).isEqualTo("Pierre D.");
    }

    @Test
    void met_l_initiale_en_majuscule_meme_accentuee() {
        assertThat(displayName("Pierre", "dupont")).isEqualTo("Pierre D.");
        assertThat(displayName("Léa", "énard")).isEqualTo("Léa É.");
    }

    @Test
    void ne_coupe_pas_un_caractere_hors_plan_de_base() {
        // U+1D49C (lettre mathématique A) : deux unités UTF-16, une seule initiale
        assertThat(displayName("Jo", "\uD835\uDC9Cbc")).isEqualTo("Jo \uD835\uDC9C.");
    }

    @Test
    void se_limite_au_prenom_sans_nom() {
        assertThat(displayName("Pierre", "")).isEqualTo("Pierre");
    }
}
