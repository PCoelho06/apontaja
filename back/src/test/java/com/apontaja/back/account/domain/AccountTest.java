package com.apontaja.back.account.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    private static final Instant NOW = Instant.parse("2020-01-01T00:00:00Z");

    private static Account account(String firstName, String lastName) {
        return new Account(UUID.randomUUID(), "a@example.com", firstName, lastName, "hash", NOW);
    }

    @Test
    void conserve_le_prenom_et_le_nom_sans_les_espaces_autour() {
        Account account = account("  Pierre ", " Dupont  ");

        assertThat(account.getFirstName()).isEqualTo("Pierre");
        assertThat(account.getLastName()).isEqualTo("Dupont");
    }

    @Test
    void refuse_un_prenom_ou_un_nom_vide_ou_blanc() {
        assertThatThrownBy(() -> account("", "Dupont")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account("   ", "Dupont")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account("Pierre", " ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refuse_un_prenom_ou_un_nom_null() {
        assertThatThrownBy(() -> account(null, "Dupont")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> account("Pierre", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void accepte_100_caracteres_et_refuse_101() {
        assertThat(account("a".repeat(100), "Dupont").getFirstName()).hasSize(100);

        assertThatThrownBy(() -> account("a".repeat(101), "Dupont")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account("Pierre", "d".repeat(101))).isInstanceOf(IllegalArgumentException.class);
    }
}
