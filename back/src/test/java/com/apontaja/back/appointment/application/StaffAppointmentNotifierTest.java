package com.apontaja.back.appointment.application;

import com.apontaja.back.appointment.application.ClientAppointmentNotification.Kind;
import com.apontaja.back.resource.application.ResourceQueryService;
import com.apontaja.back.resource.application.ResourceSummary;
import com.apontaja.back.salon.application.BookingSettings;
import com.apontaja.back.salon.application.BookingSettingsService;
import com.apontaja.back.salon.application.SalonQueryService;
import com.apontaja.back.salon.application.SalonSummary;
import com.apontaja.back.service.application.ServiceQueryService;
import com.apontaja.back.service.application.ServiceSummary;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffAppointmentNotifierTest {

    private static final Instant NOW = Instant.parse("2099-06-01T08:00:00Z");
    private static final Instant START = Instant.parse("2099-06-10T08:00:00Z");

    @Mock
    private SalonQueryService salonQueryService;
    @Mock
    private ServiceQueryService serviceQueryService;
    @Mock
    private ResourceQueryService resourceQueryService;
    @Mock
    private BookingSettingsService bookingSettingsService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final UUID salonId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    private final UUID resourceId = UUID.randomUUID();
    private final UUID profileId = UUID.randomUUID();

    private StaffAppointmentNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new StaffAppointmentNotifier(salonQueryService, serviceQueryService, resourceQueryService,
                bookingSettingsService, eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private AppointmentSummary appointment(Instant startAt) {
        return new AppointmentSummary(UUID.randomUUID(), salonId, profileId, serviceId, resourceId, startAt,
                startAt.plusSeconds(3600), "CONFIRMED", 4005, 60, NOW.minusSeconds(86400));
    }

    private void salonServiceAndResourceExist() {
        SalonSummary salon = mock(SalonSummary.class);
        lenient().when(salon.name()).thenReturn("Salon Test");
        lenient().when(salon.address()).thenReturn("1 rue du Test");
        lenient().when(salon.postalCode()).thenReturn("06140");
        lenient().when(salon.city()).thenReturn("Vence");
        lenient().when(salon.timezone()).thenReturn("Europe/Paris");
        ServiceSummary service = mock(ServiceSummary.class);
        lenient().when(service.name()).thenReturn("Coupe");
        ResourceSummary resource = mock(ResourceSummary.class);
        lenient().when(resource.name()).thenReturn("Chaise 1");
        when(salonQueryService.findAliveById(salonId)).thenReturn(Optional.of(salon));
        when(serviceQueryService.findAliveInSalon(salonId, serviceId)).thenReturn(Optional.of(service));
        when(resourceQueryService.findAliveInSalon(salonId, resourceId)).thenReturn(Optional.of(resource));
        when(bookingSettingsService.get(salonId)).thenReturn(BookingSettings.defaults(salonId));
    }

    @Test
    void publie_un_evenement_adresse_au_profil_avec_les_faits_du_rendez_vous() {
        salonServiceAndResourceExist();
        AppointmentSummary appointment = appointment(START);

        notifier.notifyClient(Kind.REQUEST_DECLINED, appointment);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ClientAppointmentNotification event = (ClientAppointmentNotification) captor.getValue();
        assertThat(event.kind()).isEqualTo(Kind.REQUEST_DECLINED);
        assertThat(event.accountId()).isNull();
        assertThat(event.customerProfileId()).isEqualTo(profileId);
        assertThat(event.appointmentId()).isEqualTo(appointment.appointmentId());
        assertThat(event.salonName()).isEqualTo("Salon Test");
        assertThat(event.timezone()).isEqualTo("Europe/Paris");
        assertThat(event.serviceName()).isEqualTo("Coupe");
        assertThat(event.resourceName()).isEqualTo("Chaise 1");
        assertThat(event.startAt()).isEqualTo(START);
        assertThat(event.priceCents()).isEqualTo(4005);
        assertThat(event.durationMinutes()).isEqualTo(60);
        assertThat(event.cancellationDeadlineHours()).isEqualTo(BookingSettings.defaults(salonId)
                .cancellationDeadlineHours());
    }

    @Test
    void n_envoie_rien_pour_un_rendez_vous_deja_commence_ou_passe() {
        notifier.notifyClient(Kind.CANCELLED_BY_SALON, appointment(NOW));
        notifier.notifyClient(Kind.CANCELLED_BY_SALON, appointment(NOW.minusSeconds(60)));

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void n_envoie_rien_si_le_salon_est_introuvable() {
        when(salonQueryService.findAliveById(salonId)).thenReturn(Optional.empty());

        notifier.notifyClient(Kind.CONFIRMED, appointment(START));

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void une_erreur_de_preparation_n_est_jamais_propagee() {
        when(salonQueryService.findAliveById(salonId)).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> notifier.notifyClient(Kind.CONFIRMED, appointment(START))).doesNotThrowAnyException();

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void prestation_ou_ressource_supprimee_ne_bloque_pas_l_email() {
        salonServiceAndResourceExist();
        when(serviceQueryService.findAliveInSalon(salonId, serviceId)).thenReturn(Optional.empty());
        when(resourceQueryService.findAliveInSalon(salonId, resourceId)).thenReturn(Optional.empty());

        notifier.notifyClient(Kind.CONFIRMED, appointment(START));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ClientAppointmentNotification event = (ClientAppointmentNotification) captor.getValue();
        assertThat(event.serviceName()).isEqualTo("-");
        assertThat(event.resourceName()).isNull();
    }
}
