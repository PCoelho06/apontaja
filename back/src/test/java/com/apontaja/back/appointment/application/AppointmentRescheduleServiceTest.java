package com.apontaja.back.appointment.application;

import com.apontaja.back.appointment.domain.Appointment;
import com.apontaja.back.appointment.domain.AppointmentRepository;
import com.apontaja.back.appointment.domain.AppointmentStatus;
import com.apontaja.back.resource.application.AvailabilityCheck;
import com.apontaja.back.resource.application.AvailabilityIssue;
import com.apontaja.back.resource.application.AvailabilityService;
import com.apontaja.back.resource.application.InvalidTimeRangeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith (MockitoExtension.class)
class AppointmentRescheduleServiceTest {

    private static final Instant OLD_START = Instant.parse("2099-03-10T09:00:00Z");
    private static final Instant OLD_END = Instant.parse("2099-03-10T09:30:00Z");
    // 10:00+01:00 = 09:00Z le lendemain ; durée conservée : 30 min
    private static final String NEW_START_TEXT = "2099-03-11T10:00:00+01:00";
    private static final Instant NEW_START = Instant.parse("2099-03-11T09:00:00Z");
    private static final Instant NEW_END = Instant.parse("2099-03-11T09:30:00Z");

    @Mock 
    private AppointmentRepository appointmentRepository;
    @Mock
    private AppointmentQueryService queryService;
    @Mock
    private AvailabilityService availabilityService;

    private AppointmentRescheduleService service;
    private final UUID salonId = UUID.randomUUID();
    private final UUID resourceId = UUID.randomUUID();
    private Appointment appointment;
    private AppointmentSummary summary;

    @BeforeEach 
    void setUp() {
        service = new AppointmentRescheduleService(appointmentRepository, queryService, availabilityService);
        appointment = new Appointment(UUID.randomUUID(), salonId, UUID.randomUUID(), UUID.randomUUID(), OLD_START,
                OLD_END, 4500, 30, Instant.parse("2020-01-01T00:00:00Z"));
        summary = AppointmentSummary.of(appointment, resourceId);
    }

    private void stubExistingAppointment() {
        when(queryService.findAliveInSalon(salonId, appointment.getId())).thenReturn(Optional.of(summary));
        when(appointmentRepository.findAliveById(appointment.getId())).thenReturn(Optional.of(appointment));
    }

    @Test 
    void reschedulesKeepingSnapshotAndRecomputingEnd() {
        stubExistingAppointment();
        when(availabilityService.check(salonId, resourceId, NEW_START, NEW_END))
                .thenReturn(new AvailabilityCheck(true, List.of()));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        AppointmentSummary result = service.reschedule(salonId, appointment.getId(), NEW_START_TEXT);

        assertThat(result.startAt()).isEqualTo(NEW_START);
        assertThat(result.endAt()).isEqualTo(NEW_END);
        assertThat(result.priceAtBookingCents()).isEqualTo(4500);
        assertThat(result.durationAtBookingMinutes()).isEqualTo(30);
        assertThat(result.resourceId()).isEqualTo(resourceId);
        assertThat(result.status()).isEqualTo(AppointmentStatus.CONFIRMED.name());
    }

    @Test
    void rejectsWhenUnavailableAndDoesNotSave() {
        stubExistingAppointment();
        when(availabilityService.check(salonId, resourceId, NEW_START, NEW_END))
                .thenReturn(new AvailabilityCheck(false, List.of(AvailabilityIssue.SALON_CLOSED)));

        assertThatThrownBy(() -> service.reschedule(salonId, appointment.getId(), NEW_START_TEXT))
                .isInstanceOf(AppointmentNotAvailableException.class);

        verify(appointmentRepository, never()).save(any(Appointment.class));
        assertThat(appointment.getStartAt()).isEqualTo(OLD_START);
    }

    @Test
    void rejectsIneligibleStatusBeforeCheckingAvailability() {
        appointment.cancel(Instant.parse("2020-01-01T00:00:00Z"), UUID.randomUUID(), null);
        stubExistingAppointment();

        assertThatThrownBy(() -> service.reschedule(salonId, appointment.getId(), NEW_START_TEXT))
                .isInstanceOf(AppointmentNotReschedulableException.class);

        verify(availabilityService, never()).check(any(), any(), any(), any());
    }

    @Test
    void mapsExclusionViolationToConflict() {
        stubExistingAppointment();
        when(availabilityService.check(salonId, resourceId, NEW_START, NEW_END))
                .thenReturn(new AvailabilityCheck(true, List.of()));
        when(appointmentRepository.save(any(Appointment.class)))
                .thenThrow(new DataIntegrityViolationException("23P01"));

        assertThatThrownBy(() -> service.reschedule(salonId, appointment.getId(), NEW_START_TEXT))
                .isInstanceOf(AppointmentConflictException.class);
    }

    @Test
    void failsWhenAppointmentNotInSalon() {
        when(queryService.findAliveInSalon(salonId, appointment.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reschedule(salonId, appointment.getId(), NEW_START_TEXT))
                .isInstanceOf(AppointmentNotFoundException.class);
    }

    @Test
    void failsOnInvalidStartAt() {
        assertThatThrownBy(() -> service.reschedule(salonId, appointment.getId(), "demain 10h"))
                .isInstanceOf(InvalidTimeRangeException.class);
    }
}
