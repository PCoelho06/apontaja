package com.apontaja.back.appointment.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class RequestExpirySchedulerTest {

    private final RequestExpiryService service = mock(RequestExpiryService.class);

    @Test
    void runsTheExpiryWhenEnabled() {
        new RequestExpiryScheduler(service, true).run();

        verify(service).expireDueRequests();
    }

    @Test
    void doesNothingWhenDisabled() {
        new RequestExpiryScheduler(service, false).run();

        verify(service, never()).expireDueRequests();
    }

    @Test
    void swallowsAFailureSoTheNextPassStillRuns() {
        when(service.expireDueRequests()).thenThrow(new IllegalStateException("boom"));

        new RequestExpiryScheduler(service, true).run();

        verify(service).expireDueRequests();
    }
}
