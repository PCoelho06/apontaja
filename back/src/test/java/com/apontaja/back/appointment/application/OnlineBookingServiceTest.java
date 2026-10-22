package com.apontaja.back.appointment.application;

import com.apontaja.back.appointment.domain.Appointment;
import com.apontaja.back.appointment.domain.AppointmentRepository;
import com.apontaja.back.appointment.domain.AppointmentResource;
import com.apontaja.back.appointment.domain.AppointmentResourceRepository;
import com.apontaja.back.appointment.domain.AppointmentStatus;
import com.apontaja.back.appointment.domain.BookingQuotaRepository;
import com.apontaja.back.appointment.domain.BusyInterval;
import com.apontaja.back.appointment.domain.ResourceBusyRepository;
import com.apontaja.back.customer.application.CustomerSelfService;
import com.apontaja.back.customer.application.SharingConsentRequiredException;
import com.apontaja.back.resource.application.InvalidTimeRangeException;
import com.apontaja.back.resource.application.ResourceQueryService;
import com.apontaja.back.resource.application.ResourceSummary;
import com.apontaja.back.service.application.ResourceTerms;
import com.apontaja.back.service.application.ServiceOffering;
import com.apontaja.back.service.application.ServiceQueryService;
import com.apontaja.back.service.application.ServiceSummary;
import com.apontaja.back.shared.domain.SalonSchedulingLock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

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

/**
 * Journée de référence : mercredi 2099-06-10 à Paris (UTC+2), créneau de 10:00
 * locale = 08:00Z.
 */
@ExtendWith(MockitoExtension.class)
class OnlineBookingServiceTest {

    private static final Instant NOW = Instant.parse("2099-06-01T00:00:00Z");
    private static final String START_TEXT = "2099-06-10T10:00:00+02:00";
    private static final Instant START = Instant.parse("2099-06-10T08:00:00Z");
    private static final String DAY = "2099-06-10";

    private final UUID accountId = UUID.randomUUID();
    private final UUID salonId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    private final UUID profileId = UUID.randomUUID();
    private final UUID r1 = new UUID(0, 1);
    private final UUID r2 = new UUID(0, 2);

    @Mock
    private PublicBookingService publicBookingService;
    @Mock
    private ServiceQueryService serviceQueryService;
    @Mock
    private ResourceQueryService resourceQueryService;
    @Mock
    private ResourceBusyRepository resourceBusyRepository;
    @Mock
    private CustomerSelfService customerSelfService;
    @Mock
    private BookingQuotaRepository bookingQuotaRepository;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private AppointmentResourceRepository appointmentResourceRepository;
    @Mock
    private SalonSchedulingLock schedulingLock;
    @Mock
    private ApplicationEventPublisher events;

    private OnlineBookingService service;

    @BeforeEach
    void setUp() {
        service = new OnlineBookingService(publicBookingService, serviceQueryService, resourceQueryService,
                resourceBusyRepository, customerSelfService, bookingQuotaRepository, appointmentRepository,
                appointmentResourceRepository, schedulingLock, () -> new UUID(7, 7), Clock.fixed(NOW, ZoneOffset.UTC),
                events);
    }

    private OnlineBookingCommand command(UUID resourceId) {
        return new OnlineBookingCommand(accountId, salonId, serviceId, resourceId, START_TEXT, "0600000000", true);
    }

    private void salon(boolean autoConfirm) {
        when(publicBookingService.salon(salonId)).thenReturn(new PublicBooking.SalonInfo(salonId, "Salon", "1 rue",
                "06140", "Vence", "France", null, "Europe/Paris", autoConfirm, 0, 365, 24));
    }

    private void offering(ResourceTerms... terms) {
        when(serviceQueryService.findOffering(salonId, serviceId)).thenReturn(Optional.of(new ServiceOffering(
                new ServiceSummary(serviceId, salonId, "Coupe", null, 60, 3000, NOW), List.of(terms))));
    }

    private void customerAndQuota(int upcoming) {
        when(customerSelfService.resolveProfileForBooking(accountId, salonId, "0600000000", true))
                .thenReturn(profileId);
        when(bookingQuotaRepository.countUpcomingActive(salonId, profileId, NOW)).thenReturn(upcoming);
    }

    private void slots(UUID requestedResource, List<UUID> freeResources, PublicBooking.Resource... resources) {
        PublicBooking.Slots slots = new PublicBooking.Slots(serviceId, "Europe/Paris", List.of(resources),
                List.of(new PublicBooking.Day(DAY, List.of(new PublicBooking.Slot(START, freeResources)))));
        when(publicBookingService.slots(salonId, serviceId, requestedResource, DAY, DAY)).thenReturn(slots);
    }

    private void persistanceEchoes() {
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentResourceRepository.save(any(AppointmentResource.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void cree_une_demande_scheduled_avec_le_snapshot_de_la_ressource_choisie() {
        salon(false);
        offering(new ResourceTerms(r1, 4000, 45));
        customerAndQuota(0);
        slots(r1, List.of(r1), new PublicBooking.Resource(r1, "Chaise 1", 4000, 45));
        persistanceEchoes();

        OnlineBookingResult result = service.book(command(r1));

        assertThat(result.status()).isEqualTo("SCHEDULED");
        assertThat(result.startAt()).isEqualTo(START);
        assertThat(result.endAt()).isEqualTo(START.plusSeconds(45 * 60L));
        assertThat(result.priceCents()).isEqualTo(4000);
        assertThat(result.durationMinutes()).isEqualTo(45);
        assertThat(result.resourceId()).isEqualTo(r1);
        assertThat(result.resourceName()).isEqualTo("Chaise 1");
        assertThat(result.serviceName()).isEqualTo("Coupe");

        ArgumentCaptor<Appointment> saved = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(saved.capture());
        assertThat(saved.getValue().getCustomerProfileId()).isEqualTo(profileId);
        ArgumentCaptor<AppointmentResource> link = ArgumentCaptor.forClass(AppointmentResource.class);
        verify(appointmentResourceRepository).save(link.capture());
        assertThat(link.getValue().getResourceId()).isEqualTo(r1);
    }

    @Test
    void prend_le_verrou_du_salon_avant_de_resoudre_le_client_et_de_recalculer_les_creneaux() {
        salon(false);
        offering(new ResourceTerms(r1, 3000, 60));
        customerAndQuota(0);
        slots(r1, List.of(r1), new PublicBooking.Resource(r1, "Chaise 1", 3000, 60));
        persistanceEchoes();

        service.book(command(r1));

        InOrder order = inOrder(schedulingLock, customerSelfService, publicBookingService, appointmentRepository);
        order.verify(schedulingLock).lock(salonId);
        order.verify(customerSelfService).resolveProfileForBooking(accountId, salonId, "0600000000", true);
        order.verify(publicBookingService).slots(salonId, serviceId, r1, DAY, DAY);
        order.verify(appointmentRepository).save(any(Appointment.class));
    }

    @Test
    void confirme_directement_quand_le_salon_confirme_automatiquement() {
        salon(true);
        offering(new ResourceTerms(r1, 3000, 60));
        customerAndQuota(0);
        slots(r1, List.of(r1), new PublicBooking.Resource(r1, "Chaise 1", 3000, 60));
        persistanceEchoes();

        assertThat(service.book(command(r1)).status()).isEqualTo(AppointmentStatus.CONFIRMED.name());
    }

    @Test
    void sans_preference_choisit_la_ressource_la_moins_chargee_du_jour() {
        salon(false);
        offering(new ResourceTerms(r1, 3000, 60), new ResourceTerms(r2, 3500, 60));
        customerAndQuota(0);
        slots(null, List.of(r1, r2), new PublicBooking.Resource(r1, "Chaise 1", 3000, 60),
                new PublicBooking.Resource(r2, "Chaise 2", 3500, 60));
        when(resourceQueryService.findAliveBySalonId(salonId)).thenReturn(List.of(
                new ResourceSummary(r1, salonId, "Chaise 1", "EMPLOYEE", Instant.parse("2020-01-01T00:00:00Z")),
                new ResourceSummary(r2, salonId, "Chaise 2", "EMPLOYEE", Instant.parse("2021-01-01T00:00:00Z"))));
        when(resourceBusyRepository.findActiveIntervals(any(), any(), any())).thenReturn(List.of(
                new BusyInterval(r1, Instant.parse("2099-06-10T07:00:00Z"), Instant.parse("2099-06-10T09:00:00Z"))));
        persistanceEchoes();

        OnlineBookingResult result = service.book(command(null));

        assertThat(result.resourceId()).isEqualTo(r2);
        assertThat(result.resourceName()).isEqualTo("Chaise 2");
        assertThat(result.priceCents()).isEqualTo(3500);
    }

    @Test
    void sans_preference_a_charge_egale_prend_la_plus_ancienne() {
        salon(false);
        offering(new ResourceTerms(r1, 3000, 60), new ResourceTerms(r2, 3000, 60));
        customerAndQuota(0);
        slots(null, List.of(r1, r2), new PublicBooking.Resource(r1, "Chaise 1", 3000, 60),
                new PublicBooking.Resource(r2, "Chaise 2", 3000, 60));
        when(resourceQueryService.findAliveBySalonId(salonId)).thenReturn(List.of(
                new ResourceSummary(r1, salonId, "Chaise 1", "EMPLOYEE", Instant.parse("2021-01-01T00:00:00Z")),
                new ResourceSummary(r2, salonId, "Chaise 2", "EMPLOYEE", Instant.parse("2020-01-01T00:00:00Z"))));
        when(resourceBusyRepository.findActiveIntervals(any(), any(), any())).thenReturn(List.of());
        persistanceEchoes();

        assertThat(service.book(command(null)).resourceId()).isEqualTo(r2);
    }

    @Test
    void refuse_un_debut_illisible_sans_rien_toucher() {
        assertThatThrownBy(
                () -> service.book(new OnlineBookingCommand(accountId, salonId, serviceId, r1, "demain", null, true)))
                        .isInstanceOf(InvalidTimeRangeException.class);

        verifyNoInteractions(publicBookingService, schedulingLock, customerSelfService);
    }

    @Test
    void salon_non_reservable_donne_le_404_uniforme_sans_verrou() {
        when(publicBookingService.salon(salonId)).thenThrow(new PublicBookingNotFoundException());

        assertThatThrownBy(() -> service.book(command(r1))).isInstanceOf(PublicBookingNotFoundException.class);

        verifyNoInteractions(schedulingLock, customerSelfService, serviceQueryService);
    }

    @Test
    void prestation_inconnue_donne_le_404_uniforme_sans_verrou() {
        salon(false);
        when(serviceQueryService.findOffering(salonId, serviceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.book(command(r1))).isInstanceOf(PublicBookingNotFoundException.class);

        verifyNoInteractions(schedulingLock, customerSelfService);
    }

    @Test
    void ressource_non_associee_a_la_prestation_donne_le_404_uniforme() {
        salon(false);
        offering(new ResourceTerms(r1, 3000, 60));

        assertThatThrownBy(() -> service.book(command(r2))).isInstanceOf(PublicBookingNotFoundException.class);

        verifyNoInteractions(schedulingLock, customerSelfService);
    }

    @Test
    void propage_l_exigence_de_consentement_sans_rien_creer() {
        salon(false);
        offering(new ResourceTerms(r1, 3000, 60));
        when(customerSelfService.resolveProfileForBooking(accountId, salonId, "0600000000", true))
                .thenThrow(new SharingConsentRequiredException());

        assertThatThrownBy(() -> service.book(command(r1))).isInstanceOf(SharingConsentRequiredException.class);

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void refuse_au_dela_de_cinq_rendez_vous_a_venir() {
        salon(false);
        offering(new ResourceTerms(r1, 3000, 60));
        customerAndQuota(OnlineBookingService.MAX_UPCOMING_PER_CUSTOMER);

        assertThatThrownBy(() -> service.book(command(r1))).isInstanceOf(BookingQuotaExceededException.class);

        verify(publicBookingService, never()).slots(any(), any(), any(), any(), any());
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void accepte_le_dernier_rendez_vous_du_quota() {
        salon(false);
        offering(new ResourceTerms(r1, 3000, 60));
        customerAndQuota(OnlineBookingService.MAX_UPCOMING_PER_CUSTOMER - 1);
        slots(r1, List.of(r1), new PublicBooking.Resource(r1, "Chaise 1", 3000, 60));
        persistanceEchoes();

        assertThat(service.book(command(r1)).status()).isEqualTo("SCHEDULED");
    }

    @Test
    void refuse_un_debut_qui_n_est_pas_un_creneau_libre() {
        salon(false);
        offering(new ResourceTerms(r1, 3000, 60));
        customerAndQuota(0);
        when(publicBookingService.slots(salonId, serviceId, r1, DAY, DAY)).thenReturn(
                new PublicBooking.Slots(serviceId, "Europe/Paris", List.of(), List.of(new PublicBooking.Day(DAY,
                        List.of(new PublicBooking.Slot(START.plusSeconds(1800), List.of(r1)))))));

        assertThatThrownBy(() -> service.book(command(r1))).isInstanceOf(SlotUnavailableException.class);

        verify(appointmentRepository, never()).save(any());
        verify(resourceQueryService, never()).findAliveBySalonId(any());
    }

    @Test
    void publie_la_notification_demande_recue_pour_une_demande_scheduled() {
        salon(false);
        offering(new ResourceTerms(r1, 4000, 45));
        customerAndQuota(0);
        slots(r1, List.of(r1), new PublicBooking.Resource(r1, "Chaise 1", 4000, 45));
        persistanceEchoes();

        service.book(command(r1));

        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(published.capture());
        ClientAppointmentNotification event = (ClientAppointmentNotification) published.getValue();
        assertThat(event.kind()).isEqualTo(ClientAppointmentNotification.Kind.REQUEST_RECEIVED);
        assertThat(event.accountId()).isEqualTo(accountId);
        assertThat(event.salonName()).isEqualTo("Salon");
        assertThat(event.salonCity()).isEqualTo("Vence");
        assertThat(event.timezone()).isEqualTo("Europe/Paris");
        assertThat(event.serviceName()).isEqualTo("Coupe");
        assertThat(event.resourceName()).isEqualTo("Chaise 1");
        assertThat(event.startAt()).isEqualTo(START);
        assertThat(event.endAt()).isEqualTo(START.plusSeconds(45 * 60L));
        assertThat(event.priceCents()).isEqualTo(4000);
        assertThat(event.durationMinutes()).isEqualTo(45);
        assertThat(event.cancellationDeadlineHours()).isEqualTo(24);
    }

    @Test
    void publie_la_notification_confirmee_quand_le_salon_confirme_automatiquement() {
        salon(true);
        offering(new ResourceTerms(r1, 3000, 60));
        customerAndQuota(0);
        slots(r1, List.of(r1), new PublicBooking.Resource(r1, "Chaise 1", 3000, 60));
        persistanceEchoes();

        service.book(command(r1));

        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(published.capture());
        assertThat(((ClientAppointmentNotification) published.getValue()).kind())
                .isEqualTo(ClientAppointmentNotification.Kind.CONFIRMED);
    }

    @Test
    void ne_publie_aucune_notification_quand_la_reservation_echoue() {
        salon(false);
        offering(new ResourceTerms(r1, 3000, 60));
        customerAndQuota(OnlineBookingService.MAX_UPCOMING_PER_CUSTOMER);

        assertThatThrownBy(() -> service.book(command(r1))).isInstanceOf(BookingQuotaExceededException.class);

        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void traduit_une_violation_d_exclusion_en_conflit() {
        salon(false);
        offering(new ResourceTerms(r1, 3000, 60));
        customerAndQuota(0);
        slots(r1, List.of(r1), new PublicBooking.Resource(r1, "Chaise 1", 3000, 60));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentResourceRepository.save(any(AppointmentResource.class)))
                .thenThrow(new DataIntegrityViolationException("23P01"));

        assertThatThrownBy(() -> service.book(command(r1))).isInstanceOf(AppointmentConflictException.class);
    }
}
