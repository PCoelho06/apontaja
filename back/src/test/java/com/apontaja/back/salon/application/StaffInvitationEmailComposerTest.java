package com.apontaja.back.salon.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.apontaja.back.salon.domain.StaffRole;

import org.junit.jupiter.api.Test;

import java.time.Duration;

class StaffInvitationEmailComposerTest {

    private static final String LINK = "http://localhost:5173/convites/aceitar?token=abc";

    @Test
    void est_en_portugais_avec_le_lien_seul_sur_sa_ligne_et_la_duree() {
        StaffInvitationEmailComposer.InvitationEmail email = StaffInvitationEmailComposer.compose(StaffRole.MANAGER,
                LINK, Duration.ofDays(7));

        assertThat(email.subject()).isEqualTo("Convite para se juntar a um salão na Apontaja");
        assertThat(email.body()).contains("\n" + LINK + "\n").contains("7 dias").contains("função: gestor")
                .contains("Apontaja");
        assertThat(email.body()).doesNotContain("Vous avez").doesNotContain("MANAGER");
    }

    @Test
    void traduit_chaque_role() {
        assertThat(StaffInvitationEmailComposer.compose(StaffRole.OWNER, LINK, Duration.ofDays(7)).body())
                .contains("função: proprietário");
        assertThat(StaffInvitationEmailComposer.compose(StaffRole.EMPLOYEE, LINK, Duration.ofDays(7)).body())
                .contains("função: colaborador");
    }

    @Test
    void une_duree_d_un_jour_est_au_singulier() {
        assertThat(StaffInvitationEmailComposer.compose(StaffRole.EMPLOYEE, LINK, Duration.ofDays(1)).body())
                .contains("1 dia)").doesNotContain("1 dias");
    }
}
