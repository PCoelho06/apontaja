package com.apontaja.back.appointment.application;

import com.apontaja.back.appointment.domain.Appointment;
import com.apontaja.back.appointment.domain.AppointmentRepository;
import com.apontaja.back.appointment.domain.AppointmentResourceRepository;

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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentQueryServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AppointmentResourceRepository appointmentResourceRepository;

    private AppointmentQueryService queryService;

    private final UUID salonId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-09-24T08:00:00Z");

    @BeforeEach
    void setUp() {
        queryService = new AppointmentQueryService(appointmentRepository, appointmentResourceRepository);
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
}
