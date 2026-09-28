package com.apontaja.back.appointment.application;

import com.apontaja.back.appointment.domain.Appointment;
import com.apontaja.back.appointment.domain.AppointmentRepository;
import com.apontaja.back.appointment.domain.AppointmentResource;
import com.apontaja.back.appointment.domain.AppointmentResourceRepository;
import com.apontaja.back.customer.application.CustomerQueryService;
import com.apontaja.back.customer.application.CustomerSummary;
import com.apontaja.back.resource.application.AvailabilityCheck;
import com.apontaja.back.resource.application.AvailabilityIssue;
import com.apontaja.back.resource.application.AvailabilityService;
import com.apontaja.back.resource.application.InvalidTimeRangeException;
import com.apontaja.back.resource.application.ResourceNotFoundException;
import com.apontaja.back.resource.application.ResourceQueryService;
import com.apontaja.back.resource.application.ResourceSummary;
import com.apontaja.back.service.application.ServiceNotFoundException;
import com.apontaja.back.service.application.ServiceQueryService;
import com.apontaja.back.service.application.ServiceSummary;
import com.apontaja.back.service.application.ServiceTerms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentCreationServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AppointmentResourceRepository appointmentResourceRepository;

    @Mock
    private ResourceQueryService resourceQueryService;

    @Mock
    private ServiceQueryService serviceQueryService;

    @Mock
    private CustomerQueryService customerQueryService;

    @Mock
    private AvailabilityService availabilityService;

    private AppointmentCreationService service;

    private final Instant fixedNow = Instant.parse("2026-09-24T07:00:00Z");
    private final UUID salonId = UUID.randomUUID();
    private final UUID resourceId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    private final UUID customerProfileId = UUID.randomUUID();
    private static final String START_AT = "2026-09-24T10:00:00+02:00";
    private static final Instant EXPECTED_START = Instant.parse("2026-09-24T08:00:00Z");

    @BeforeEach
    void setUp() {
        service = new AppointmentCreationService(appointmentRepository, appointmentResourceRepository,
                resourceQueryService, serviceQueryService, customerQueryService, availabilityService,
                () -> new UUID(0, 1), Clock.fixed(fixedNow, ZoneOffset.UTC));
    }

    private CreateAppointmentCommand command() {
        return new CreateAppointmentCommand(salonId, customerProfileId, serviceId, resourceId, START_AT);
    }

    private ResourceSummary aliveResource() {
        return new ResourceSummary(resourceId, salonId, "Chaise", "EMPLOYEE", fixedNow);
    }

    private ServiceSummary aliveService() {
        return new ServiceSummary(serviceId, salonId, "Coupe", null, 60, 2500, fixedNow);
    }

    private CustomerSummary aliveCustomer() {
        return new CustomerSummary(customerProfileId, UUID.randomUUID(), salonId, "Alice", "Martin", "a@example.com",
                null, null, fixedNow);
    }

    private void allReferencesValid() {
        when(resourceQueryService.findAliveInSalon(salonId, resourceId)).thenReturn(Optional.of(aliveResource()));
        when(serviceQueryService.findAliveInSalon(salonId, serviceId)).thenReturn(Optional.of(aliveService()));
        when(customerQueryService.findAliveInSalon(salonId, customerProfileId))
                .thenReturn(Optional.of(aliveCustomer()));
    }

    @Test
    void refuse_une_date_de_debut_illisible_sans_toucher_aux_collaborateurs() {
        assertThatThrownBy(() -> service.create(new CreateAppointmentCommand(salonId, customerProfileId, serviceId,
                resourceId, "pas-une-date"))).isInstanceOf(InvalidTimeRangeException.class);

        verifyNoInteractions(resourceQueryService, serviceQueryService, customerQueryService, availabilityService,
                appointmentRepository, appointmentResourceRepository);
    }

    @Test
    void refuse_une_ressource_absente_du_salon() {
        when(resourceQueryService.findAliveInSalon(salonId, resourceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(command())).isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(serviceQueryService, customerQueryService, availabilityService, appointmentRepository);
    }

    @Test
    void refuse_une_prestation_absente_du_salon() {
        when(resourceQueryService.findAliveInSalon(salonId, resourceId)).thenReturn(Optional.of(aliveResource()));
        when(serviceQueryService.findAliveInSalon(salonId, serviceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(command())).isInstanceOf(ServiceNotFoundException.class);

        verifyNoInteractions(customerQueryService, availabilityService, appointmentRepository);
    }

    @Test
    void refuse_un_client_absent_du_salon() {
        when(resourceQueryService.findAliveInSalon(salonId, resourceId)).thenReturn(Optional.of(aliveResource()));
        when(serviceQueryService.findAliveInSalon(salonId, serviceId)).thenReturn(Optional.of(aliveService()));
        when(customerQueryService.findAliveInSalon(salonId, customerProfileId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(command())).isInstanceOf(CustomerNotFoundException.class);

        verifyNoInteractions(availabilityService, appointmentRepository);
    }

    @Test
    void refuse_une_ressource_non_associee_a_la_prestation() {
        allReferencesValid();
        when(serviceQueryService.resolveTerms(salonId, serviceId, resourceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(command()))
                .isInstanceOf(ServiceResourceNotAssociatedException.class);

        verifyNoInteractions(availabilityService, appointmentRepository);
    }

    @Test
    void refuse_un_creneau_indisponible_en_conservant_les_violations() {
        allReferencesValid();
        when(serviceQueryService.resolveTerms(salonId, serviceId, resourceId))
                .thenReturn(Optional.of(new ServiceTerms(2500, 60)));
        when(availabilityService.check(salonId, resourceId, EXPECTED_START, EXPECTED_START.plusSeconds(3600)))
                .thenReturn(new AvailabilityCheck(false,
                        List.of(AvailabilityIssue.OUTSIDE_SALON_HOURS, AvailabilityIssue.SALON_CLOSED)));

        assertThatThrownBy(() -> service.create(command())).isInstanceOf(AppointmentNotAvailableException.class)
                .satisfies(ex -> assertThat(((AppointmentNotAvailableException) ex).issues())
                        .containsExactly(AvailabilityIssue.OUTSIDE_SALON_HOURS, AvailabilityIssue.SALON_CLOSED));

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void reserve_avec_le_snapshot_prix_duree_effectif_de_la_ressource() {
        allReferencesValid();
        when(serviceQueryService.resolveTerms(salonId, serviceId, resourceId))
                .thenReturn(Optional.of(new ServiceTerms(4000, 45)));
        when(availabilityService.check(salonId, resourceId, EXPECTED_START, EXPECTED_START.plusSeconds(45 * 60L)))
                .thenReturn(new AvailabilityCheck(true, List.of()));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentResourceRepository.save(any(AppointmentResource.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AppointmentSummary result = service.create(command());

        assertThat(result.priceAtBookingCents()).isEqualTo(4000);
        assertThat(result.durationAtBookingMinutes()).isEqualTo(45);
        assertThat(result.startAt()).isEqualTo(EXPECTED_START);
        assertThat(result.endAt()).isEqualTo(EXPECTED_START.plusSeconds(45 * 60L));
        assertThat(result.status()).isEqualTo("CONFIRMED");
        assertThat(result.resourceId()).isEqualTo(resourceId);

        ArgumentCaptor<AppointmentResource> linkCaptor = ArgumentCaptor.forClass(AppointmentResource.class);
        verify(appointmentResourceRepository).save(linkCaptor.capture());
        assertThat(linkCaptor.getValue().getResourceId()).isEqualTo(resourceId);
    }

    @Test
    void traduit_une_violation_d_exclusion_concurrente_en_conflit_metier() {
        allReferencesValid();
        when(serviceQueryService.resolveTerms(salonId, serviceId, resourceId))
                .thenReturn(Optional.of(new ServiceTerms(2500, 60)));
        when(availabilityService.check(any(), any(), any(), any()))
                .thenReturn(new AvailabilityCheck(true, List.of()));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentResourceRepository.save(any(AppointmentResource.class)))
                .thenThrow(new DataIntegrityViolationException("exclusion_violation"));

        assertThatThrownBy(() -> service.create(command())).isInstanceOf(AppointmentConflictException.class);
    }
}
