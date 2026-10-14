package com.apontaja.back.salon.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IanaTimezoneValidatorTest {

    private final IanaTimezoneValidator validator = new IanaTimezoneValidator();

    @Test
    void accepte_les_identifiants_de_region_connus() {
        assertThat(validator.isValid("Europe/Paris", null)).isTrue();
        assertThat(validator.isValid("America/New_York", null)).isTrue();
        assertThat(validator.isValid("Asia/Tokyo", null)).isTrue();
        assertThat(validator.isValid("UTC", null)).isTrue();
    }

    @Test
    void refuse_les_identifiants_inconnus_decales_ou_mal_formes() {
        assertThat(validator.isValid("Paris", null)).isFalse();
        assertThat(validator.isValid("Europe/Nulle", null)).isFalse();
        assertThat(validator.isValid(" Europe/Paris", null)).isFalse();
        assertThat(validator.isValid("Europe/Paris ", null)).isFalse();
        assertThat(validator.isValid("europe/paris", null)).isFalse();
        assertThat(validator.isValid("+02:00", null)).isFalse();
    }

    @Test
    void laisse_not_blank_traiter_les_valeurs_vides() {
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid("", null)).isTrue();
        assertThat(validator.isValid("   ", null)).isTrue();
    }
}
