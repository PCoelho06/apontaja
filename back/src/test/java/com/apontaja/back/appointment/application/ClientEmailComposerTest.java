package com.apontaja.back.appointment.application;

import com.apontaja.back.appointment.application.ClientAppointmentNotification.Kind;
import com.apontaja.back.appointment.application.ClientEmailComposer.ClientEmail;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Mercredi 2099-06-10, 10:00 à Paris (UTC+2) = 08:00Z. */
class ClientEmailComposerTest {

    private static final Instant START = Instant.parse("2099-06-10T08:00:00Z");

    private static ClientAppointmentNotification notification(Kind kind, int deadlineHours) {
        return new ClientAppointmentNotification(kind, UUID.randomUUID(), UUID.randomUUID(), "Salon Test",
                "1 rue du Test", "06140", "Vence", "Europe/Paris", "Coupe", "Chaise 1", START,
                START.plusSeconds(45 * 60L), 4005, 45, deadlineHours);
    }

    @Test
    void pedido_recebido_est_en_portugais_avec_l_heure_du_salon() {
        ClientEmail email = ClientEmailComposer.compose(notification(Kind.REQUEST_RECEIVED, 24), "Alice");

        assertThat(email.subject()).isEqualTo("Pedido de marcação recebido - Salon Test");
        assertThat(email.body()).startsWith("Olá, Alice,").contains("O salão ainda tem de o validar")
                .contains("Serviço: Coupe").contains("Com: Chaise 1").contains("10 de junho de 2099 às 10:00")
                .contains("(hora de Europe/Paris)").contains("Duração: 45 min").contains("Preço: 40,05 €")
                .contains("Local: 1 rue du Test, 06140 Vence").contains("até ao início da marcação")
                .endsWith("Com os melhores cumprimentos,\nApontaja");
    }

    @Test
    void marcacao_confirmada_indique_la_limite_d_annulation() {
        ClientEmail email = ClientEmailComposer.compose(notification(Kind.CONFIRMED, 24), "Alice");

        assertThat(email.subject()).isEqualTo("Marcação confirmada - Salon Test");
        assertThat(email.body()).contains("está confirmada").contains("Pode cancelar até")
                .contains("9 de junho de 2099 às 10:00");
    }

    @Test
    void marcacao_confirmada_sans_delai_annulable_jusqu_au_debut() {
        ClientEmail email = ClientEmailComposer.compose(notification(Kind.CONFIRMED, 0), "Alice");

        assertThat(email.body()).contains("Pode cancelar até ao início da marcação.");
    }

    @Test
    void marcacao_cancelada_confirme_l_annulation() {
        ClientEmail email = ClientEmailComposer.compose(notification(Kind.CANCELLED, 24), "Alice");

        assertThat(email.subject()).isEqualTo("Marcação cancelada - Salon Test");
        assertThat(email.body()).contains("Confirmamos o cancelamento").contains("nova marcação")
                .doesNotContain("Pode cancelar");
    }

    @Test
    void formate_les_prix_ronds_et_les_centimes_a_un_chiffre() {
        ClientAppointmentNotification n = new ClientAppointmentNotification(Kind.CONFIRMED, UUID.randomUUID(),
                UUID.randomUUID(), "S", "a", "1", "v", "Europe/Paris", "C", "R", START, START.plusSeconds(60),
                3000, 1, 0);

        assertThat(ClientEmailComposer.compose(n, "A").body()).contains("Preço: 30,00 €");
        assertThat(ClientEmailComposer.compose(new ClientAppointmentNotification(Kind.CONFIRMED, n.accountId(),
                n.appointmentId(), "S", "a", "1", "v", "Europe/Paris", "C", "R", START, START.plusSeconds(60), 5,
                1, 0), "A").body()).contains("Preço: 0,05 €");
    }

    @Test
    void pedido_recusado_informe_le_refus_sans_limite_d_annulation() {
        ClientEmail email = ClientEmailComposer.compose(notification(Kind.REQUEST_DECLINED, 24), "Alice");

        assertThat(email.subject()).isEqualTo("Pedido de marcação recusado - Salon Test");
        assertThat(email.body()).startsWith("Olá, Alice,").contains("não pôde ser aceite pelo salão")
                .contains("novo pedido").contains("Serviço: Coupe").doesNotContain("Pode cancelar");
    }

    @Test
    void marcacao_cancelada_pelo_salao_presente_des_excuses() {
        ClientEmail email = ClientEmailComposer.compose(notification(Kind.CANCELLED_BY_SALON, 24), "Alice");

        assertThat(email.subject()).isEqualTo("Marcação cancelada pelo salão - Salon Test");
        assertThat(email.body()).contains("foi cancelada pelo salão").contains("Pedimos desculpa")
                .doesNotContain("a seu pedido");
    }
}
