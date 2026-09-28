package com.apontaja.back.appointment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.apontaja.back.appointment.domain.AppointmentStatus;
import com.apontaja.back.resource.application.BlockingAppointment;

@ExtendWith(MockitoExtension.class)
class ClosureConflictCheckAdapterTest {

    private static final Instant CREATED = Instant.parse("2020-01-01T00:00:00Z");
    private static final Instant START = Instant.parse("2099-01-05T08:00:00Z");
    private static final Instant END = Instant.parse("2099-01-05T12:00:00Z");
    private static final String START_TEXT = "2099-01-05T08:00:00Z";
    private static final String END_TEXT = "2099-01-05T12:00:00Z";

    @Mock
    private AppointmentQueryService queryService;

    private ClosureConflictCheckAdapter adapter;
    private final UUID salonId = UUID.randomUUID();
    private final UUID resourceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adapter = new ClosureConflictCheckAdapter(queryService);
    }

    private AppointmentSummary summary(AppointmentStatus status, String startAt, String endAt) {
        return new AppointmentSummary(UUID.randomUUID(), salonId, UUID.randomUUID(), UUID.randomUUID(), resourceId,
                Instant.parse(startAt), Instant.parse(endAt), status.name(), 2500, 60, CREATED);
    }

    @Test
    void keepsOnlyScheduledAndConfirmedAppointments() {
        AppointmentSummary scheduled = summary(AppointmentStatus.SCHEDULED, "2099-01-05T09:00:00Z",
                "2099-01-05T10:00:00Z");
        AppointmentSummary confirmed = summary(AppointmentStatus.CONFIRMED, "2099-01-05T10:00:00Z",
                "2099-01-05T11:00:00Z");
        when(queryService.list(salonId, null, START_TEXT, END_TEXT)).thenReturn(List.of(scheduled,
                summary(AppointmentStatus.CANCELLED, "2099-01-05T08:00:00Z", "2099-01-05T09:00:00Z"), confirmed,
                summary(AppointmentStatus.COMPLETED, "2099-01-05T11:00:00Z", "2099-01-05T12:00:00Z"),
                summary(AppointmentStatus.NO_SHOW, "2099-01-05T11:00:00Z", "2099-01-05T12:00:00Z")));

        List<BlockingAppointment> result = adapter.findBlockingAppointments(salonId, null, START, END);

        assertThat(result).extracting(BlockingAppointment::appointmentId).containsExactly(
                scheduled.appointmentId(), confirmed.appointmentId());
        assertThat(result.get(0).resourceId()).isEqualTo(resourceId);
        assertThat(result.get(0).startAt()).isEqualTo(Instant.parse("2099-01-05T09:00:00Z"));
    }

    @Test
    void scopesTheAgendaQueryToTheResourceWhenGiven() {
        when(queryService.list(salonId, resourceId, START_TEXT, END_TEXT)).thenReturn(List.of());

        assertThat(adapter.findBlockingAppointments(salonId, resourceId, START, END)).isEmpty();
    }
}
