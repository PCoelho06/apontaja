package com.apontaja.back.appointment.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.apontaja.back.appointment.application.ClientAppointmentNotification.Kind;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

class ClientEmailComposerExpiryTest {

    @Test
    void composesTheExpiredRequestEmail() {
        ClientAppointmentNotification notification = new ClientAppointmentNotification(Kind.REQUEST_EXPIRED, null,
                UUID.randomUUID(), "Salão Teste", "Rua do Teste 1", "1000-001", "Lisboa", "Europe/Lisbon", "Corte",
                "Ana", Instant.parse("2099-06-10T09:00:00Z"), Instant.parse("2099-06-10T09:30:00Z"), 2500, 30, 24,
                UUID.randomUUID());

        ClientEmailComposer.ClientEmail email = ClientEmailComposer.compose(notification, "Maria");

        assertThat(email.subject()).isEqualTo("Pedido de marcação expirado - Salão Teste");
        assertThat(email.body()).contains("Olá, Maria,").contains("expirou").contains("Salão Teste")
                .contains("Corte").contains("10:00").contains("novo pedido");
    }
}
