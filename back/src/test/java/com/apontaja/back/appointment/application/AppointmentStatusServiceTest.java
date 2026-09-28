package com.apontaja.back.appointment.application;

import com.apontaja.back.appointment.domain.Appointment;
import com.apontaja.back.appointment.domain.AppointmentRepository;
import com.apontaja.back.appointment.domain.AppointmentResourceRepository;
import com.apontaja.back.appointment.domain.AppointmentStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentStatusServiceTest {

    private static final Instant START = Instant.parse("2026-09-24T08:00:00Z");

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AppointmentResourceRepository appointmentResourceRepository;

    private final UUID salonId = UUID.randomUUID();
    private final UUID resourceId = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();

    private AppointmentStatusService serviceAt(Instant now) {
        return new AppointmentStatusService(appointmentRepository, appointmentResourceRepository,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private AppointmentStatusService service;

    @BeforeEach
    void setUp() {
        service = serviceAt(START.plusSeconds(3600));
    }

    /** Statut posé par réflexion : le constructeur produit toujours CONFIRMED. */
    private Appointment appointmentWith(AppointmentStatus status) {
        Appointment appointment = new Appointment(UUID.randomUUID(), salonId, UUID.randomUUID(),
                UUID.randomUUID(), START, START.plusSeconds(3600), 2500, 60, START.minusSeconds(86400));
        try {
            var field = Appointment.class.getDeclaredField("status");
            field.setAccessible(true);
            field.set(appointment, status);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        when(appointmentRepository.findAliveById(appointment.getId())).thenReturn(Optional.of(appointment));
        return appointment;
    }

    private void persistenceWorks(Appointment appointment) {
        when(appointmentRepository.save(appointment)).thenReturn(appointment);
        when(appointmentResourceRepository.findResourceIdsByAppointmentId(appointment.getId()))
                .thenReturn(List.of(resourceId));
    }

    @Test
    void confirme_un_rdv_scheduled() {
        Appointment appointment = appointmentWith(AppointmentStatus.SCHEDULED);
        persistenceWorks(appointment);

        AppointmentSummary result = service.confirm(salonId, appointment.getId());

        assertThat(result.status()).isEqualTo("CONFIRMED");
        assertThat(result.resourceId()).isEqualTo(resourceId);
    }

    @Test
    void refuse_de_confirmer_un_rdv_deja_confirme_ou_termine() {
        for (AppointmentStatus status : List.of(AppointmentStatus.CONFIRMED, AppointmentStatus.COMPLETED,
                AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW)) {
            Appointment appointment = appointmentWith(status);

            assertThatThrownBy(() -> service.confirm(salonId, appointment.getId()))
                    .isInstanceOf(InvalidAppointmentTransitionException.class);
        }
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void annule_un_rdv_scheduled_ou_confirmed_avec_trace_de_l_acteur_et_du_motif_normalise() {
        for (AppointmentStatus status : List.of(AppointmentStatus.SCHEDULED, AppointmentStatus.CONFIRMED)) {
            Appointment appointment = appointmentWith(status);
            persistenceWorks(appointment);

            AppointmentSummary result = service.cancel(salonId, appointment.getId(), actor, "  Client malade  ");

            assertThat(result.status()).isEqualTo("CANCELLED");
            assertThat(appointment.getCancelledBy()).isEqualTo(actor);
            assertThat(appointment.getCancelledAt()).isEqualTo(START.plusSeconds(3600));
            assertThat(appointment.getCancellationReason()).isEqualTo("Client malade");
        }
    }

    @Test
    void un_motif_vide_est_enregistre_comme_absent() {
        Appointment appointment = appointmentWith(AppointmentStatus.CONFIRMED);
        persistenceWorks(appointment);

        service.cancel(salonId, appointment.getId(), actor, "   ");

        assertThat(appointment.getCancellationReason()).isNull();
    }

    @Test
    void refuse_d_annuler_un_rdv_termine_absent_ou_deja_annule() {
        for (AppointmentStatus status : List.of(AppointmentStatus.COMPLETED, AppointmentStatus.NO_SHOW,
                AppointmentStatus.CANCELLED)) {
            Appointment appointment = appointmentWith(status);

            assertThatThrownBy(() -> service.cancel(salonId, appointment.getId(), actor, null))
                    .isInstanceOf(InvalidAppointmentTransitionException.class);
        }
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void termine_uniquement_un_rdv_confirme() {
        Appointment confirmed = appointmentWith(AppointmentStatus.CONFIRMED);
        persistenceWorks(confirmed);

        assertThat(service.complete(salonId, confirmed.getId()).status()).isEqualTo("COMPLETED");

        for (AppointmentStatus status : List.of(AppointmentStatus.SCHEDULED, AppointmentStatus.COMPLETED,
                AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW)) {
            Appointment other = appointmentWith(status);

            assertThatThrownBy(() -> service.complete(salonId, other.getId()))
                    .isInstanceOf(InvalidAppointmentTransitionException.class);
        }
    }

    @Test
    void no_show_est_refuse_avant_l_heure_de_debut() {
        Appointment appointment = appointmentWith(AppointmentStatus.CONFIRMED);

        assertThatThrownBy(() -> serviceAt(START.minusSeconds(1)).markNoShow(salonId, appointment.getId()))
                .isInstanceOf(InvalidAppointmentTransitionException.class);

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void no_show_est_accepte_pile_a_l_heure_de_debut_et_apres() {
        for (Instant now : List.of(START, START.plusSeconds(1))) {
            Appointment appointment = appointmentWith(AppointmentStatus.CONFIRMED);
            persistenceWorks(appointment);

            assertThat(serviceAt(now).markNoShow(salonId, appointment.getId()).status()).isEqualTo("NO_SHOW");
        }
    }

    @Test
    void no_show_est_refuse_sur_un_rdv_termine_ou_annule() {
        for (AppointmentStatus status : List.of(AppointmentStatus.COMPLETED, AppointmentStatus.CANCELLED,
                AppointmentStatus.NO_SHOW)) {
            Appointment appointment = appointmentWith(status);

            assertThatThrownBy(() -> service.markNoShow(salonId, appointment.getId()))
                    .isInstanceOf(InvalidAppointmentTransitionException.class);
        }
    }

    @Test
    void un_rdv_d_un_autre_salon_ou_inconnu_est_introuvable() {
        Appointment appointment = appointmentWith(AppointmentStatus.CONFIRMED);
        UUID unknownId = UUID.randomUUID();
        when(appointmentRepository.findAliveById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(UUID.randomUUID(), appointment.getId(), actor, null))
                .isInstanceOf(AppointmentNotFoundException.class);
        assertThatThrownBy(() -> service.complete(salonId, unknownId))
                .isInstanceOf(AppointmentNotFoundException.class);
    }

    @Test
    void signale_une_incoherence_si_aucune_ressource_n_est_liee() {
        Appointment appointment = appointmentWith(AppointmentStatus.CONFIRMED);
        when(appointmentRepository.save(appointment)).thenReturn(appointment);
        when(appointmentResourceRepository.findResourceIdsByAppointmentId(appointment.getId()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.complete(salonId, appointment.getId()))
                .isInstanceOf(IllegalStateException.class);
    }
}
