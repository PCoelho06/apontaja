package com.apontaja.back.appointment.domain;

import com.apontaja.back.appointment.domain.CancellationPolicy.Verdict;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CancellationPolicyTest {

    private static final Instant START = Instant.parse("2099-06-10T08:00:00Z");

    @Test
    void demande_non_validee_annulable_jusqu_au_debut_exclu() {
        assertThat(CancellationPolicy.evaluate(AppointmentStatus.SCHEDULED, START, 24, START.minusSeconds(1)))
                .isEqualTo(Verdict.ALLOWED);
        assertThat(CancellationPolicy.evaluate(AppointmentStatus.SCHEDULED, START, 24, START))
                .isEqualTo(Verdict.NOT_ALLOWED);
        assertThat(CancellationPolicy.evaluate(AppointmentStatus.SCHEDULED, START, 24, START.plusSeconds(1)))
                .isEqualTo(Verdict.NOT_ALLOWED);
    }

    @Test
    void la_demande_non_validee_ignore_le_delai_d_annulation() {
        assertThat(CancellationPolicy.evaluate(AppointmentStatus.SCHEDULED, START, 720, START.minusSeconds(60)))
                .isEqualTo(Verdict.ALLOWED);
    }

    @Test
    void rdv_confirme_annulable_jusqu_au_delai_inclus() {
        Instant limit = START.minusSeconds(24 * 3600L);

        assertThat(CancellationPolicy.evaluate(AppointmentStatus.CONFIRMED, START, 24, limit.minusSeconds(1)))
                .isEqualTo(Verdict.ALLOWED);
        assertThat(CancellationPolicy.evaluate(AppointmentStatus.CONFIRMED, START, 24, limit))
                .isEqualTo(Verdict.ALLOWED);
        assertThat(CancellationPolicy.evaluate(AppointmentStatus.CONFIRMED, START, 24, limit.plusSeconds(1)))
                .isEqualTo(Verdict.DEADLINE_PASSED);
    }

    @Test
    void rdv_confirme_sans_delai_annulable_jusqu_au_debut_inclus() {
        assertThat(CancellationPolicy.evaluate(AppointmentStatus.CONFIRMED, START, 0, START))
                .isEqualTo(Verdict.ALLOWED);
        assertThat(CancellationPolicy.evaluate(AppointmentStatus.CONFIRMED, START, 0, START.plusSeconds(1)))
                .isEqualTo(Verdict.DEADLINE_PASSED);
    }

    @Test
    void les_autres_statuts_ne_sont_pas_annulables() {
        for (AppointmentStatus status : new AppointmentStatus[] { AppointmentStatus.COMPLETED,
                AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW }) {
            assertThat(CancellationPolicy.evaluate(status, START, 24, START.minusSeconds(100_000)))
                    .isEqualTo(Verdict.NOT_ALLOWED);
            assertThat(CancellationPolicy.cancellableUntil(status, START, 24)).isNull();
        }
    }

    @Test
    void calcule_le_dernier_instant_d_annulation() {
        assertThat(CancellationPolicy.cancellableUntil(AppointmentStatus.SCHEDULED, START, 24)).isEqualTo(START);
        assertThat(CancellationPolicy.cancellableUntil(AppointmentStatus.CONFIRMED, START, 24))
                .isEqualTo(Instant.parse("2099-06-09T08:00:00Z"));
    }
}
