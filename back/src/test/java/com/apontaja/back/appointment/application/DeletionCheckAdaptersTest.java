package com.apontaja.back.appointment.application;

import com.apontaja.back.appointment.domain.AppointmentRepository;
import com.apontaja.back.appointment.domain.AppointmentResourceRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeletionCheckAdaptersTest {

    @Mock
    private AppointmentResourceRepository appointmentResourceRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Test
    void resource_deletion_check_delegue_a_existsActiveByResourceId() {
        UUID resourceId = UUID.randomUUID();
        when(appointmentResourceRepository.existsActiveByResourceId(resourceId)).thenReturn(true);

        assertThat(new ResourceDeletionCheckAdapter(appointmentResourceRepository)
                .hasBlockingAppointments(resourceId)).isTrue();
    }

    @Test
    void service_deletion_check_delegue_a_existsActiveByServiceId() {
        UUID serviceId = UUID.randomUUID();
        when(appointmentRepository.existsActiveByServiceId(serviceId)).thenReturn(false);

        assertThat(new ServiceDeletionCheckAdapter(appointmentRepository).hasBlockingAppointments(serviceId))
                .isFalse();
    }
}
