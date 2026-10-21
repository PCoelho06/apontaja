package com.apontaja.back.account.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FrontendLinksTest {

    private final FrontendLinks links = new FrontendLinks("http://salon.test", "http://client.test");

    @Test
    void pointe_vers_le_portail_demande() {
        assertThat(links.link(FrontendPortal.SALON, "/confirmer-email?token=abc"))
                .isEqualTo("http://salon.test/confirmer-email?token=abc");
        assertThat(links.link(FrontendPortal.CLIENT, "/confirmer-email?token=abc"))
                .isEqualTo("http://client.test/confirmer-email?token=abc");
    }

    @Test
    void utilise_le_portail_salon_quand_aucun_portail_n_est_precise() {
        assertThat(links.link(null, "/x")).isEqualTo("http://salon.test/x");
    }
}
