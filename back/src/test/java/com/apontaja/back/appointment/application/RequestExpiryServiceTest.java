package com.apontaja.back.appointment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apontaja.back.appointment.domain.AppointmentExpiryRepository;
import com.apontaja.back.appointment.domain.PendingRequest;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

class RequestExpiryServiceTest {

    private static final Instant NOW = Instant.parse("2099-01-03T10:00:00Z");

    private final AppointmentExpiryRepository repository = mock(AppointmentExpiryRepository.class);
    private final RequestExpiryProcessor processor = mock(RequestExpiryProcessor.class);
    private final RequestExpiryService service = new RequestExpiryService(repository, processor,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void countsExpiredRequestsAndKeepsGoingAfterAFailure() {
        PendingRequest first = new PendingRequest(UUID.randomUUID(), UUID.randomUUID());
        PendingRequest broken = new PendingRequest(UUID.randomUUID(), UUID.randomUUID());
        PendingRequest third = new PendingRequest(UUID.randomUUID(), UUID.randomUUID());
        PendingRequest alreadyHandled = new PendingRequest(UUID.randomUUID(), UUID.randomUUID());
        when(repository.findDue(NOW, RequestExpiryService.BATCH_SIZE))
                .thenReturn(List.of(first, broken, third, alreadyHandled));
        when(processor.expire(first.appointmentId(), first.salonId(), NOW)).thenReturn(true);
        when(processor.expire(broken.appointmentId(), broken.salonId(), NOW))
                .thenThrow(new IllegalStateException("boom"));
        when(processor.expire(third.appointmentId(), third.salonId(), NOW)).thenReturn(true);
        when(processor.expire(alreadyHandled.appointmentId(), alreadyHandled.salonId(), NOW)).thenReturn(false);

        int expired = service.expireDueRequests();

        assertThat(expired).isEqualTo(2);
        verify(processor).expire(third.appointmentId(), third.salonId(), NOW);
    }

    @Test
    void doesNothingWhenNothingIsDue() {
        when(repository.findDue(NOW, RequestExpiryService.BATCH_SIZE)).thenReturn(List.of());

        assertThat(service.expireDueRequests()).isZero();
    }
}
