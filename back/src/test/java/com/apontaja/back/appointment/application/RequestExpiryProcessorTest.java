package com.apontaja.back.appointment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apontaja.back.appointment.application.ClientAppointmentNotification.Kind;
import com.apontaja.back.appointment.domain.Appointment;
import com.apontaja.back.appointment.domain.AppointmentRepository;
import com.apontaja.back.appointment.domain.AppointmentResourceRepository;
import com.apontaja.back.appointment.domain.AppointmentStatus;
import com.apontaja.back.appointment.domain.RequestExpiryPolicy;
import com.apontaja.back.shared.domain.SalonSchedulingLock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class RequestExpiryProcessorTest {

    private static final Instant CREATED = Instant.parse("2099-01-01T10:00:00Z");
    private static final Instant START = Instant.parse("2099-06-10T09:00:00Z");
    private static final Instant DUE = Instant.parse("2099-01-03T10:00:00Z");

    private final UUID salonId = UUID.randomUUID();
    private final UUID resourceId = UUID.randomUUID();
    private final AppointmentRepository appointmentRepository = mock(AppointmentRepository.class);
    private final AppointmentResourceRepository resourceRepository = mock(AppointmentResourceRepository.class);
    private final SalonSchedulingLock lock = mock(SalonSchedulingLock.class);
    private final StaffAppointmentNotifier notifier = mock(StaffAppointmentNotifier.class);
    private final RequestExpiryProcessor processor = new RequestExpiryProcessor(appointmentRepository,
            resourceRepository, lock, notifier);

    private Appointment appointment(AppointmentStatus status) {
        Appointment appointment = new Appointment(UUID.randomUUID(), salonId, UUID.randomUUID(),
                UUID.randomUUID(), START, START.plusSeconds(1800), 2500, 30, CREATED,
                AppointmentStatus.SCHEDULED);
        if (status == AppointmentStatus.CONFIRMED) {
            appointment.confirm();
        }
        return appointment;
    }

    @BeforeEach
    void stubSave() {
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void expiresADueRequestAndNotifiesTheClient() {
        Appointment appointment = appointment(AppointmentStatus.SCHEDULED);
        when(appointmentRepository.findAliveById(appointment.getId())).thenReturn(Optional.of(appointment));
        when(resourceRepository.findResourceIdsByAppointmentId(appointment.getId()))
                .thenReturn(List.of(resourceId));

        boolean expired = processor.expire(appointment.getId(), salonId, DUE);

        assertThat(expired).isTrue();
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(appointment.getCancelledAt()).isEqualTo(DUE);
        assertThat(appointment.getCancelledBy()).isNull();
        assertThat(appointment.getCancellationReason()).isEqualTo(RequestExpiryPolicy.EXPIRY_REASON);
        verify(lock).lock(salonId);
        ArgumentCaptor<AppointmentSummary> summary = ArgumentCaptor.forClass(AppointmentSummary.class);
        verify(notifier).notifyClient(eq(Kind.REQUEST_EXPIRED), summary.capture());
        assertThat(summary.getValue().resourceId()).isEqualTo(resourceId);
    }

    @Test
    void doesNothingBeforeTheDeadline() {
        Appointment appointment = appointment(AppointmentStatus.SCHEDULED);
        when(appointmentRepository.findAliveById(appointment.getId())).thenReturn(Optional.of(appointment));

        boolean expired = processor.expire(appointment.getId(), salonId, DUE.minusSeconds(1));

        assertThat(expired).isFalse();
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
        verify(notifier, never()).notifyClient(any(), any());
    }

    @Test
    void leavesARequestConfirmedInTheMeantime() {
        Appointment appointment = appointment(AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findAliveById(appointment.getId())).thenReturn(Optional.of(appointment));

        boolean expired = processor.expire(appointment.getId(), salonId, DUE);

        assertThat(expired).isFalse();
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        verify(appointmentRepository, never()).save(any());
        verify(notifier, never()).notifyClient(any(), any());
    }

    @Test
    void ignoresAnUnknownOrForeignAppointment() {
        Appointment appointment = appointment(AppointmentStatus.SCHEDULED);
        when(appointmentRepository.findAliveById(appointment.getId())).thenReturn(Optional.of(appointment));

        assertThat(processor.expire(appointment.getId(), UUID.randomUUID(), DUE)).isFalse();
        assertThat(processor.expire(UUID.randomUUID(), salonId, DUE)).isFalse();
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
    }

    @Test
    void stillExpiresWhenTheResourceLinkIsMissing() {
        Appointment appointment = appointment(AppointmentStatus.SCHEDULED);
        when(appointmentRepository.findAliveById(appointment.getId())).thenReturn(Optional.of(appointment));
        when(resourceRepository.findResourceIdsByAppointmentId(appointment.getId())).thenReturn(List.of());

        assertThat(processor.expire(appointment.getId(), salonId, DUE)).isTrue();
        verify(notifier).notifyClient(eq(Kind.REQUEST_EXPIRED), any(AppointmentSummary.class));
    }
}
