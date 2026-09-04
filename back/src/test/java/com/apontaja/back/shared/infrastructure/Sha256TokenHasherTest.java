package com.apontaja.back.shared.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Sha256TokenHasherTest {

    @Test
    void meme_entree_donne_toujours_le_meme_hash() {
        Sha256TokenHasher hasher = new Sha256TokenHasher();

        assertThat(hasher.hash("valeur")).isEqualTo(hasher.hash("valeur"));
    }

    @Test
    void entrees_differentes_donnent_des_hash_differents() {
        Sha256TokenHasher hasher = new Sha256TokenHasher();

        assertThat(hasher.hash("valeur-a")).isNotEqualTo(hasher.hash("valeur-b"));
    }
}
