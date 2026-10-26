package com.apontaja.back.account.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FrontendLinksEmailPathsTest {

    private final FrontendLinks links = new FrontendLinks("http://salon.test", "http://client.test");

    @Test
    void le_portail_salon_utilise_les_chemins_portugais() {
        assertThat(links.confirmEmailLink(FrontendPortal.SALON, "abc"))
                .isEqualTo("http://salon.test/confirmar-email?token=abc");
        assertThat(links.resetPasswordLink(FrontendPortal.SALON, "abc"))
                .isEqualTo("http://salon.test/repor-palavra-passe?token=abc");
    }

    @Test
    void le_portail_client_utilise_les_chemins_portugais() {
        assertThat(links.confirmEmailLink(FrontendPortal.CLIENT, "abc"))
                .isEqualTo("http://client.test/confirmar-email?token=abc");
        assertThat(links.resetPasswordLink(FrontendPortal.CLIENT, "abc"))
                .isEqualTo("http://client.test/repor-palavra-passe?token=abc");
    }

    @Test
    void sans_portail_le_comportement_historique_du_salon_est_conserve() {
        assertThat(links.confirmEmailLink(null, "abc")).isEqualTo("http://salon.test/confirmar-email?token=abc");
        assertThat(links.resetPasswordLink(null, "abc"))
                .isEqualTo("http://salon.test/repor-palavra-passe?token=abc");
    }
}
