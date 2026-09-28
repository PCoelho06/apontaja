package com.apontaja.back.resource.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ClosureBlockedByAppointmentsExceptionTest {

    private static final Instant BASE = Instant.parse("2099-01-05T00:00:00Z");

    private static BlockingAppointment at(int index) {
        Instant start = BASE.plusSeconds(index * 7200L);
        return new BlockingAppointment(UUID.randomUUID(), UUID.randomUUID(), start, start.plusSeconds(3600));
    }

    @Test
    void reportsTheTotalButListsOnlyTheFirstTwentySortedByStart() {
        List<BlockingAppointment> blocking = new ArrayList<>();
        for (int i = 24; i >= 0; i--) {
            blocking.add(at(i));
        }

        ClosureBlockedByAppointmentsException ex = new ClosureBlockedByAppointmentsException(blocking);

        assertThat(ex.count()).isEqualTo(25);
        assertThat(ex.appointments()).hasSize(ClosureBlockedByAppointmentsException.MAX_LISTED);
        assertThat(ex.appointments()).isSortedAccordingTo(Comparator.comparing(BlockingAppointment::startAt));
        assertThat(ex.appointments().get(0).startAt()).isEqualTo(BASE);
        assertThat(ex.getMessage()).contains("25");
    }
}
