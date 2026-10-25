package com.apontaja.back.account.application;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AuthEmailComposerTest {

    private static final String LINK = "http://localhost:5174/confirmar-email?token=abc";

    @Test
    void verification_client_est_en_portugais_avec_le_lien_et_la_duree() {
        AuthEmailComposer.AuthEmail email = AuthEmailComposer.verification(FrontendPortal.CLIENT, LINK,
                Duration.ofHours(48));

        assertThat(email.subject()).isEqualTo("Confirme o seu endereço de email");
        assertThat(email.body()).contains("\n" + LINK + "\n").contains("48 horas").contains("Apontaja");
        assertThat(email.body()).doesNotContain("Cliquez");
    }

    @Test
    void verification_salon_et_null_restent_en_francais_a_l_identique() {
        for (FrontendPortal portal : new FrontendPortal[] { FrontendPortal.SALON, null }) {
            AuthEmailComposer.AuthEmail email = AuthEmailComposer.verification(portal, LINK, Duration.ofHours(48));

            assertThat(email.subject()).isEqualTo("Confirmez votre adresse email");
            assertThat(email.body()).isEqualTo(
                    "Cliquez sur ce lien pour confirmer votre email (valable 48h) : " + LINK);
        }
    }

    @Test
    void reinitialisation_client_est_en_portugais_avec_le_lien_et_la_duree() {
        AuthEmailComposer.AuthEmail email = AuthEmailComposer.passwordReset(FrontendPortal.CLIENT, LINK,
                Duration.ofHours(1));

        assertThat(email.subject()).isEqualTo("Reposição da sua palavra-passe");
        assertThat(email.body()).contains("\n" + LINK + "\n").contains("60 minutos");
        assertThat(email.body()).doesNotContain("Cliquez");
    }

    @Test
    void reinitialisation_salon_et_null_restent_en_francais_a_l_identique() {
        for (FrontendPortal portal : new FrontendPortal[] { FrontendPortal.SALON, null }) {
            AuthEmailComposer.AuthEmail email = AuthEmailComposer.passwordReset(portal, LINK, Duration.ofHours(1));

            assertThat(email.subject()).isEqualTo("Réinitialisation de votre mot de passe");
            assertThat(email.body()).isEqualTo(
                    "Cliquez sur ce lien pour choisir un nouveau mot de passe (valable 60 min) : " + LINK);
        }
    }

    @Test
    void les_durees_d_une_unite_sont_au_singulier() {
        assertThat(AuthEmailComposer.verification(FrontendPortal.CLIENT, LINK, Duration.ofHours(1)).body())
                .contains("1 hora)").doesNotContain("1 horas");
        assertThat(AuthEmailComposer.passwordReset(FrontendPortal.CLIENT, LINK, Duration.ofMinutes(1)).body())
                .contains("1 minuto)").doesNotContain("1 minutos");
    }
}
