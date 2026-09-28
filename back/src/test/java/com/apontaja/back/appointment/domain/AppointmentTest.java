package com.apontaja.back.appointment.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppointmentTest {

    private static final Instant NOW = Instant.parse("2026-09-24T08:00:00Z");

    private static Appointment appointment(Instant start, Instant end, int price, int duration) {
        return new Appointment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), start,
                end, price, duration, NOW);
    }

    @Test
    void est_toujours_confirmed_a_la_creation() {
        Appointment appointment = appointment(NOW, NOW.plusSeconds(1800), 2500, 30);

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(appointment.isDeleted()).isFalse();
    }

    @Test
    void les_mutateurs_de_statut_sont_inconditionnels() {
        Appointment appointment = appointment(NOW, NOW.plusSeconds(1800), 2500, 30);

        appointment.complete();
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);

        Instant cancelledAt = NOW.plusSeconds(60);
        UUID cancelledBy = UUID.randomUUID();
        appointment.cancel(cancelledAt, cancelledBy, "Client indisponible");
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(appointment.getCancelledAt()).isEqualTo(cancelledAt);
        assertThat(appointment.getCancelledBy()).isEqualTo(cancelledBy);
        assertThat(appointment.getCancellationReason()).isEqualTo("Client indisponible");

        appointment.markNoShow();
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.NO_SHOW);

        appointment.confirm();
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    @Test
    void rejette_une_fin_non_posterieure_au_debut() {
        assertThatThrownBy(() -> appointment(NOW, NOW, 2500, 30)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> appointment(NOW, NOW.minusSeconds(60), 2500, 30))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejette_un_prix_negatif() {
        assertThatThrownBy(() -> appointment(NOW, NOW.plusSeconds(1800), -1, 30))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejette_une_duree_hors_bornes() {
        assertThatThrownBy(() -> appointment(NOW, NOW.plusSeconds(1800), 2500, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> appointment(NOW, NOW.plusSeconds(1800), 2500, 1441))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
