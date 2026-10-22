package com.apontaja.back.appointment.application;

import com.apontaja.back.appointment.application.ClientAppointmentNotification.Kind;
import com.apontaja.back.appointment.domain.Appointment;
import com.apontaja.back.appointment.domain.AppointmentRepository;
import com.apontaja.back.appointment.domain.AppointmentStatus;
import com.apontaja.back.appointment.domain.ClientAppointmentRepository;
import com.apontaja.back.appointment.domain.ClientAppointmentRow;
import com.apontaja.back.appointment.domain.ClientAppointmentRows;
import com.apontaja.back.salon.application.BookingSettings;
import com.apontaja.back.salon.application.BookingSettingsService;
import com.apontaja.back.shared.domain.SalonSchedulingLock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientAppointmentServiceTest {

    private static final Instant NOW = Instant.parse("2099-06-01T00:00:00Z");
    private static final Instant START = Instant.parse("2099-06-10T08:00:00Z");

    private final UUID accountId = UUID.randomUUID();
    private final UUID salonId = UUID.randomUUID();
    private final UUID appointmentId = UUID.randomUUID();

    @Mock
    private ClientAppointmentRepository clientAppointmentRepository;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private BookingSettingsService bookingSettingsService;
    @Mock
    private SalonSchedulingLock schedulingLock;
    @Mock
    private ApplicationEventPublisher events;

    private ClientAppointmentService service;

    @BeforeEach
    void setUp() {
        service = new ClientAppointmentService(clientAppointmentRepository, appointmentRepository,
                bookingSettingsService, schedulingLock, events, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void deadline(UUID salon, int hours) {
        when(bookingSettingsService.get(salon)).thenReturn(new BookingSettings(salon, true, false, 15, 0, 365,
                hours));
    }

    private ClientAppointmentRow row(UUID salon, UUID id, Instant start, String status, Instant cancelledAt) {
        return new ClientAppointmentRow(id, salon, "Salon Test", "1 rue du Test", "06140", "Vence",
                "Europe/Paris", UUID.randomUUID(), "Coupe", UUID.randomUUID(), "Chaise 1", start,
                start.plusSeconds(3600), status, 3000, 60, cancelledAt);
    }

    private Appointment entity(AppointmentStatus status, Instant start) {
        return new Appointment(appointmentId, salonId, UUID.randomUUID(), UUID.randomUUID(), start,
                start.plusSeconds(3600), 3000, 60, NOW, status);
    }

    @Test
    void liste_les_rdv_a_venir_avec_les_indicateurs_d_annulation() {
        UUID otherSalon = UUID.randomUUID();
        ClientAppointmentRow scheduled = row(salonId, UUID.randomUUID(), START, "SCHEDULED", null);
        ClientAppointmentRow confirmed = row(salonId, UUID.randomUUID(), START.plusSeconds(86_400), "CONFIRMED",
                null);
        ClientAppointmentRow tooLate = row(otherSalon, UUID.randomUUID(), NOW.plusSeconds(3600), "CONFIRMED", null);
        when(clientAppointmentRepository.findForAccount(accountId, true, NOW, 0, 20))
                .thenReturn(new ClientAppointmentRows(List.of(scheduled, confirmed, tooLate), 3));
        deadline(salonId, 24);
        deadline(otherSalon, 48);

        ClientAppointmentPage page = service.list(accountId, ClientAppointmentScope.UPCOMING, null, null);

        assertThat(page.content()).hasSize(3);
        ClientAppointment first = page.content().get(0);
        assertThat(first.cancellable()).isTrue();
        assertThat(first.cancellableUntil()).isEqualTo(START);
        assertThat(first.salon().name()).isEqualTo("Salon Test");
        assertThat(first.salon().timezone()).isEqualTo("Europe/Paris");
        ClientAppointment second = page.content().get(1);
        assertThat(second.cancellable()).isTrue();
        assertThat(second.cancellableUntil()).isEqualTo(START);
        assertThat(page.content().get(2).cancellable()).isFalse();
        assertThat(page.content().get(2).cancellableUntil()).isEqualTo(NOW.plusSeconds(3600 - 48 * 3600L));
        verify(bookingSettingsService).get(salonId);
        verify(bookingSettingsService).get(otherSalon);
    }

    @Test
    void liste_les_rdv_passes_sans_possibilite_d_annuler() {
        Instant cancelledAt = Instant.parse("2099-05-01T00:00:00Z");
        ClientAppointmentRow cancelled = row(salonId, UUID.randomUUID(), START, "CANCELLED", cancelledAt);
        when(clientAppointmentRepository.findForAccount(accountId, false, NOW, 2, 10))
                .thenReturn(new ClientAppointmentRows(List.of(cancelled), 21));
        deadline(salonId, 24);

        ClientAppointmentPage page = service.list(accountId, ClientAppointmentScope.PAST, 2, 10);

        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(10);
        assertThat(page.totalElements()).isEqualTo(21);
        assertThat(page.totalPages()).isEqualTo(3);
        ClientAppointment item = page.content().get(0);
        assertThat(item.cancellable()).isFalse();
        assertThat(item.cancellableUntil()).isNull();
        assertThat(item.cancelledAt()).isEqualTo(cancelledAt);
        assertThat(item.status()).isEqualTo("CANCELLED");
    }

    @Test
    void borne_la_page_et_la_taille() {
        when(clientAppointmentRepository.findForAccount(accountId, true, NOW, 0, 50))
                .thenReturn(new ClientAppointmentRows(List.of(), 0));

        ClientAppointmentPage page = service.list(accountId, ClientAppointmentScope.UPCOMING, -3, 500);

        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(50);
        assertThat(page.totalPages()).isZero();
        verifyNoInteractions(bookingSettingsService);
    }

    @Test
    void taille_nulle_ou_negative_revient_a_la_valeur_par_defaut() {
        when(clientAppointmentRepository.findForAccount(accountId, true, NOW, 0, 20))
                .thenReturn(new ClientAppointmentRows(List.of(), 0));

        assertThat(service.list(accountId, ClientAppointmentScope.UPCOMING, 0, 0).size()).isEqualTo(20);
    }

    @Test
    void annule_une_demande_non_validee_sous_le_verrou_et_publie_l_email() {
        ClientAppointmentRow before = row(salonId, appointmentId, START, "SCHEDULED", null);
        ClientAppointmentRow after = row(salonId, appointmentId, START, "CANCELLED", NOW);
        when(clientAppointmentRepository.findOwned(accountId, appointmentId))
                .thenReturn(Optional.of(before), Optional.of(after));
        Appointment appointment = entity(AppointmentStatus.SCHEDULED, START);
        when(appointmentRepository.findAliveById(appointmentId)).thenReturn(Optional.of(appointment));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));
        deadline(salonId, 24);

        ClientAppointment result = service.cancel(accountId, appointmentId);

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(appointment.getCancelledAt()).isEqualTo(NOW);
        assertThat(appointment.getCancelledBy()).isEqualTo(accountId);
        assertThat(appointment.getCancellationReason()).isEqualTo("Annulé par le client");
        assertThat(result.status()).isEqualTo("CANCELLED");
        assertThat(result.cancellable()).isFalse();

        InOrder order = inOrder(schedulingLock, appointmentRepository);
        order.verify(schedulingLock).lock(salonId);
        order.verify(appointmentRepository).findAliveById(appointmentId);
        order.verify(appointmentRepository).save(appointment);

        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(published.capture());
        ClientAppointmentNotification event = (ClientAppointmentNotification) published.getValue();
        assertThat(event.kind()).isEqualTo(Kind.CANCELLED);
        assertThat(event.accountId()).isEqualTo(accountId);
        assertThat(event.salonName()).isEqualTo("Salon Test");
        assertThat(event.timezone()).isEqualTo("Europe/Paris");
    }

    @Test
    void annule_un_rdv_confirme_dans_le_delai() {
        ClientAppointmentRow before = row(salonId, appointmentId, START, "CONFIRMED", null);
        when(clientAppointmentRepository.findOwned(accountId, appointmentId))
                .thenReturn(Optional.of(before), Optional.of(row(salonId, appointmentId, START, "CANCELLED", NOW)));
        Appointment appointment = entity(AppointmentStatus.CONFIRMED, START);
        when(appointmentRepository.findAliveById(appointmentId)).thenReturn(Optional.of(appointment));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));
        deadline(salonId, 24);

        service.cancel(accountId, appointmentId);

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void refuse_un_rdv_confirme_hors_delai_sans_rien_modifier() {
        Instant soon = NOW.plusSeconds(2 * 3600L);
        when(clientAppointmentRepository.findOwned(accountId, appointmentId))
                .thenReturn(Optional.of(row(salonId, appointmentId, soon, "CONFIRMED", null)));
        Appointment appointment = entity(AppointmentStatus.CONFIRMED, soon);
        when(appointmentRepository.findAliveById(appointmentId)).thenReturn(Optional.of(appointment));
        deadline(salonId, 24);

        assertThatThrownBy(() -> service.cancel(accountId, appointmentId))
                .isInstanceOf(CancellationDeadlinePassedException.class);

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        verify(appointmentRepository, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void refuse_une_demande_deja_commencee() {
        when(clientAppointmentRepository.findOwned(accountId, appointmentId))
                .thenReturn(Optional.of(row(salonId, appointmentId, NOW.minusSeconds(60), "SCHEDULED", null)));
        when(appointmentRepository.findAliveById(appointmentId))
                .thenReturn(Optional.of(entity(AppointmentStatus.SCHEDULED, NOW.minusSeconds(60))));
        deadline(salonId, 24);

        assertThatThrownBy(() -> service.cancel(accountId, appointmentId))
                .isInstanceOf(AppointmentNotCancellableException.class);
    }

    @Test
    void refuse_un_statut_non_annulable_lu_sous_le_verrou() {
        // La lecture avant verrou voyait SCHEDULED ; le staff a depuis marqué le RDV terminé.
        when(clientAppointmentRepository.findOwned(accountId, appointmentId))
                .thenReturn(Optional.of(row(salonId, appointmentId, START, "SCHEDULED", null)));
        Appointment completed = entity(AppointmentStatus.CONFIRMED, START);
        completed.complete();
        when(appointmentRepository.findAliveById(appointmentId)).thenReturn(Optional.of(completed));
        deadline(salonId, 24);

        assertThatThrownBy(() -> service.cancel(accountId, appointmentId))
                .isInstanceOf(AppointmentNotCancellableException.class);

        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void rdv_d_un_autre_compte_ou_inconnu_donne_le_404_uniforme_sans_verrou() {
        when(clientAppointmentRepository.findOwned(accountId, appointmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(accountId, appointmentId))
                .isInstanceOf(ClientAppointmentNotFoundException.class);

        verifyNoInteractions(schedulingLock, appointmentRepository, events);
    }

    @Test
    void rdv_supprime_entre_temps_donne_le_404_uniforme() {
        when(clientAppointmentRepository.findOwned(accountId, appointmentId))
                .thenReturn(Optional.of(row(salonId, appointmentId, START, "SCHEDULED", null)));
        when(appointmentRepository.findAliveById(appointmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(accountId, appointmentId))
                .isInstanceOf(ClientAppointmentNotFoundException.class);
    }

    @Test
    void parse_le_scope_sans_tenir_compte_de_la_casse_et_refuse_le_reste() {
        assertThat(ClientAppointmentScope.parse("Upcoming")).isEqualTo(ClientAppointmentScope.UPCOMING);
        assertThat(ClientAppointmentScope.parse(" past ")).isEqualTo(ClientAppointmentScope.PAST);
        assertThatThrownBy(() -> ClientAppointmentScope.parse(null))
                .isInstanceOf(InvalidClientAppointmentQueryException.class);
        assertThatThrownBy(() -> ClientAppointmentScope.parse("all"))
                .isInstanceOf(InvalidClientAppointmentQueryException.class);
    }
}
