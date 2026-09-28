package com.apontaja.back.appointment.application;

import com.apontaja.back.appointment.domain.Appointment;
import com.apontaja.back.appointment.domain.AppointmentRepository;
import com.apontaja.back.appointment.domain.AppointmentResource;
import com.apontaja.back.appointment.domain.AppointmentResourceRepository;
import com.apontaja.back.resource.application.InvalidTimeRangeException;
import com.apontaja.back.resource.application.ResourceNotFoundException;
import com.apontaja.back.resource.application.ResourceQueryService;
import com.apontaja.back.resource.application.ResourceSummary;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentQueryServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AppointmentResourceRepository appointmentResourceRepository;

    @Mock
    private ResourceQueryService resourceQueryService;

    private AppointmentQueryService queryService;

    private final UUID salonId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-09-24T08:00:00Z");

    @BeforeEach
    void setUp() {
        queryService = new AppointmentQueryService(appointmentRepository, appointmentResourceRepository,
                resourceQueryService);
    }

    private Appointment appointment(UUID salon) {
        return new Appointment(UUID.randomUUID(), salon, UUID.randomUUID(), UUID.randomUUID(), now,
                now.plusSeconds(1800), 2500, 30, now);
    }

    @Test
    void retourne_le_rdv_avec_sa_ressource_quand_tout_correspond() {
        Appointment appointment = appointment(salonId);
        UUID resourceId = UUID.randomUUID();
        when(appointmentRepository.findAliveById(appointment.getId())).thenReturn(Optional.of(appointment));
        when(appointmentResourceRepository.findResourceIdsByAppointmentId(appointment.getId()))
                .thenReturn(List.of(resourceId));

        assertThat(queryService.findAliveInSalon(salonId, appointment.getId()))
                .hasValueSatisfying(summary -> assertThat(summary.resourceId()).isEqualTo(resourceId));
    }

    @Test
    void est_vide_pour_un_rdv_d_un_autre_salon() {
        Appointment appointment = appointment(UUID.randomUUID());
        when(appointmentRepository.findAliveById(appointment.getId())).thenReturn(Optional.of(appointment));

        assertThat(queryService.findAliveInSalon(salonId, appointment.getId())).isEmpty();
    }

    @Test
    void est_vide_pour_un_rdv_inconnu() {
        UUID appointmentId = UUID.randomUUID();
        when(appointmentRepository.findAliveById(appointmentId)).thenReturn(Optional.empty());

        assertThat(queryService.findAliveInSalon(salonId, appointmentId)).isEmpty();
    }

    @Test
    void signale_une_incoherence_si_aucune_ressource_n_est_liee() {
        Appointment appointment = appointment(salonId);
        when(appointmentRepository.findAliveById(appointment.getId())).thenReturn(Optional.of(appointment));
        when(appointmentResourceRepository.findResourceIdsByAppointmentId(appointment.getId()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> queryService.findAliveInSalon(salonId, appointment.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void list_resout_les_ressources_en_lot_sans_filtre_de_ressource() {
        Appointment first = appointment(salonId);
        Appointment second = appointment(salonId);
        UUID resourceA = UUID.randomUUID();
        UUID resourceB = UUID.randomUUID();
        when(appointmentRepository.findOverlappingBySalonId(eq(salonId), isNull(), any(), any()))
                .thenReturn(List.of(first, second));
        when(appointmentResourceRepository.findByAppointmentIds(any())).thenReturn(
                List.of(new AppointmentResource(first.getId(), resourceA),
                        new AppointmentResource(second.getId(), resourceB)));

        List<AppointmentSummary> result = queryService.list(salonId, null, null, null);

        assertThat(result).extracting(AppointmentSummary::resourceId).containsExactly(resourceA, resourceB);
        verify(resourceQueryService, never()).findAliveInSalon(any(), any());
    }

    @Test
    void list_refuse_une_ressource_absente_du_salon() {
        UUID resourceId = UUID.randomUUID();
        when(resourceQueryService.findAliveInSalon(salonId, resourceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.list(salonId, resourceId, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void list_transmet_le_filtre_de_ressource_et_les_bornes_converties_en_instants() {
        UUID resourceId = UUID.randomUUID();
        when(resourceQueryService.findAliveInSalon(salonId, resourceId)).thenReturn(Optional
                .of(new ResourceSummary(resourceId, salonId, "Chaise", "EMPLOYEE", now)));
        when(appointmentRepository.findOverlappingBySalonId(any(), any(), any(), any())).thenReturn(List.of());
        when(appointmentResourceRepository.findByAppointmentIds(any())).thenReturn(List.of());

        queryService.list(salonId, resourceId, "2026-09-24T00:00:00+02:00", "2026-09-25T00:00:00+02:00");

        verify(appointmentRepository).findOverlappingBySalonId(salonId, resourceId,
                Instant.parse("2026-09-23T22:00:00Z"), Instant.parse("2026-09-24T22:00:00Z"));
    }

    @Test
    void list_refuse_des_bornes_inversees_ou_illisibles() {
        assertThatThrownBy(() -> queryService.list(salonId, null, "2026-09-25T00:00:00Z", "2026-09-24T00:00:00Z"))
                .isInstanceOf(InvalidTimeRangeException.class);
        assertThatThrownBy(() -> queryService.list(salonId, null, "hier", null))
                .isInstanceOf(InvalidTimeRangeException.class);
    }

    @Test
    void list_signale_une_incoherence_si_un_rdv_n_a_pas_de_ressource() {
        Appointment orphan = appointment(salonId);
        when(appointmentRepository.findOverlappingBySalonId(any(), any(), any(), any()))
                .thenReturn(List.of(orphan));
        when(appointmentResourceRepository.findByAppointmentIds(any())).thenReturn(List.of());

        assertThatThrownBy(() -> queryService.list(salonId, null, null, null))
                .isInstanceOf(IllegalStateException.class);
    }
}
