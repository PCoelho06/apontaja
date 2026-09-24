package com.apontaja.back.resource.domain;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduleAndClosureTest {

    private final Instant now = Instant.parse("2026-09-24T10:00:00Z");

    @Test
    void schedule_de_salon_n_a_pas_de_ressource_et_inversement() {
        UUID ownerId = UUID.randomUUID();
        Schedule forSalon = Schedule.forSalon(UUID.randomUUID(), ownerId, DayOfWeek.SUNDAY, LocalTime.of(9, 0),
                LocalTime.of(12, 0), now);
        Schedule forResource = Schedule.forResource(UUID.randomUUID(), ownerId, DayOfWeek.MONDAY, LocalTime.of(9, 0),
                LocalTime.of(12, 0), now);

        assertThat(forSalon.getSalonId()).isEqualTo(ownerId);
        assertThat(forSalon.getResourceId()).isNull();
        assertThat(forSalon.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(forResource.getResourceId()).isEqualTo(ownerId);
        assertThat(forResource.getSalonId()).isNull();
    }

    @Test
    void schedule_rejette_une_fin_non_posterieure_au_debut() {
        assertThatThrownBy(() -> Schedule.forSalon(UUID.randomUUID(), UUID.randomUUID(), DayOfWeek.MONDAY,
                LocalTime.of(12, 0), LocalTime.of(12, 0), now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Schedule.forSalon(UUID.randomUUID(), UUID.randomUUID(), DayOfWeek.MONDAY,
                LocalTime.of(12, 0), LocalTime.of(9, 0), now)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void closure_porte_un_seul_proprietaire_et_rejette_une_periode_inversee() {
        UUID ownerId = UUID.randomUUID();
        Closure forSalon = Closure.forSalon(UUID.randomUUID(), ownerId, now, now.plusSeconds(3600), null, now);
        Closure forResource = Closure.forResource(UUID.randomUUID(), ownerId, now, now.plusSeconds(3600), "x", now);

        assertThat(forSalon.getResourceId()).isNull();
        assertThat(forResource.getSalonId()).isNull();
        assertThatThrownBy(() -> Closure.forSalon(UUID.randomUUID(), ownerId, now, now, null, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> forSalon.reschedule(now.plusSeconds(10), now, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
