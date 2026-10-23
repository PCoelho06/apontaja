package com.apontaja.back.appointment.application;

import com.apontaja.back.appointment.application.ClientAppointmentNotification.Kind;
import com.apontaja.back.customer.application.ClientContact;
import com.apontaja.back.customer.application.ClientContactQueryService;
import com.apontaja.back.shared.domain.EmailSender;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientAppointmentEmailListenerTest {

    private static final Instant START = Instant.parse("2099-06-10T08:00:00Z");

    private final UUID accountId = UUID.randomUUID();

    @Mock
    private ClientContactQueryService contactQueryService;
    @Mock
    private EmailSender emailSender;

    private ClientAppointmentNotification event(String timezone) {
        return new ClientAppointmentNotification(Kind.CONFIRMED, accountId, UUID.randomUUID(), "Salon", "1 rue",
                "06140", "Vence", timezone, "Coupe", "Chaise 1", START, START.plusSeconds(3600), 3000, 60, 24);
    }

    private ClientAppointmentEmailListener listener() {
        return new ClientAppointmentEmailListener(contactQueryService, emailSender);
    }

    @Test
    void envoie_l_email_a_l_adresse_du_compte() {
        when(contactQueryService.find(accountId)).thenReturn(Optional.of(new ClientContact("a@example.com",
                "Alice")));

        listener().onNotification(event("Europe/Paris"));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(eq("a@example.com"), eq("Marcação confirmada - Salon"), body.capture());
        assertThat(body.getValue()).startsWith("Olá, Alice,");
    }

    @Test
    void n_envoie_rien_si_le_compte_n_existe_plus() {
        when(contactQueryService.find(accountId)).thenReturn(Optional.empty());

        listener().onNotification(event("Europe/Paris"));

        verify(emailSender, never()).send(any(), any(), any());
    }

    @Test
    void un_echec_d_envoi_n_est_jamais_propage() {
        when(contactQueryService.find(accountId)).thenReturn(Optional.of(new ClientContact("a@example.com",
                "Alice")));
        doThrow(new IllegalStateException("smtp down")).when(emailSender).send(any(), any(), any());

        assertThatCode(() -> listener().onNotification(event("Europe/Paris"))).doesNotThrowAnyException();
    }

    @Test
    void un_echec_de_composition_n_est_jamais_propage() {
        when(contactQueryService.find(accountId)).thenReturn(Optional.of(new ClientContact("a@example.com",
                "Alice")));

        assertThatCode(() -> listener().onNotification(event("Pas/UnFuseau"))).doesNotThrowAnyException();

        verify(emailSender, never()).send(any(), any(), any());
    }

    @Test
    void un_echec_de_recherche_du_contact_n_est_jamais_propage() {
        when(contactQueryService.find(accountId)).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> listener().onNotification(event("Europe/Paris"))).doesNotThrowAnyException();
    }

    private ClientAppointmentNotification staffEvent(UUID profileId) {
        return new ClientAppointmentNotification(Kind.REQUEST_DECLINED, null, UUID.randomUUID(), "Salon", "1 rue",
                "06140", "Vence", "Europe/Paris", "Coupe", "Chaise 1", START, START.plusSeconds(3600), 3000, 60, 24,
                profileId);
    }

    @Test
    void un_evenement_du_staff_est_adresse_via_le_profil() {
        UUID profileId = UUID.randomUUID();
        when(contactQueryService.findForProfile(profileId))
                .thenReturn(Optional.of(new ClientContact("p@example.com", "Paul")));

        listener().onNotification(staffEvent(profileId));

        verify(emailSender).send(eq("p@example.com"), eq("Pedido de marcação recusado - Salon"), any());
        verify(contactQueryService, never()).find(any());
    }

    @Test
    void un_profil_sans_email_ne_recoit_rien() {
        UUID profileId = UUID.randomUUID();
        when(contactQueryService.findForProfile(profileId)).thenReturn(Optional.empty());

        listener().onNotification(staffEvent(profileId));

        verify(emailSender, never()).send(any(), any(), any());
    }

    @Test
    void un_evenement_sans_destinataire_est_ignore() {
        listener().onNotification(staffEvent(null));

        verify(emailSender, never()).send(any(), any(), any());
    }
}
