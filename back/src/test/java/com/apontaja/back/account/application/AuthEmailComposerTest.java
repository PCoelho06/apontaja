package com.apontaja.back.account.application;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AuthEmailComposerTest {

    private static final String LINK = "http://localhost:5174/confirmar-email?token=abc";

    private static final FrontendPortal[] ALL_PORTALS = { FrontendPortal.CLIENT, FrontendPortal.SALON, null };

    @Test
    void verification_est_en_portugais_pour_tous_les_portails_avec_le_lien_et_la_duree() {
        for (FrontendPortal portal : ALL_PORTALS) {
            AuthEmailComposer.AuthEmail email = AuthEmailComposer.verification(portal, LINK, Duration.ofHours(48));

            assertThat(email.subject()).isEqualTo("Confirme o seu endereço de email");
            assertThat(email.body()).contains("\n" + LINK + "\n").contains("48 horas").contains("Apontaja");
            assertThat(email.body()).doesNotContain("Cliquez");
        }
    }

    @Test
    void reinitialisation_est_en_portugais_pour_tous_les_portails_avec_le_lien_et_la_duree() {
        for (FrontendPortal portal : ALL_PORTALS) {
            AuthEmailComposer.AuthEmail email = AuthEmailComposer.passwordReset(portal, LINK, Duration.ofHours(1));

            assertThat(email.subject()).isEqualTo("Reposição da sua palavra-passe");
            assertThat(email.body()).contains("\n" + LINK + "\n").contains("60 minutos").contains("Apontaja");
            assertThat(email.body()).doesNotContain("Cliquez");
        }
    }

    @Test
    void les_durees_d_une_unite_sont_au_singulier() {
        assertThat(AuthEmailComposer.verification(FrontendPortal.SALON, LINK, Duration.ofHours(1)).body())
                .contains("1 hora)").doesNotContain("1 horas");
        assertThat(AuthEmailComposer.passwordReset(FrontendPortal.SALON, LINK, Duration.ofMinutes(1)).body())
                .contains("1 minuto)").doesNotContain("1 minutos");
    }
}
