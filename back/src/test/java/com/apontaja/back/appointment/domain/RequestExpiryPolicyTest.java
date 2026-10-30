package com.apontaja.back.appointment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.time.Instant;

class RequestExpiryPolicyTest {

    private static final Instant CREATED = Instant.parse("2099-06-01T10:00:00Z");

    @Test
    void expiresAfterFortyEightHoursWhenTheStartIsFarAway() {
        Instant start = Instant.parse("2099-06-20T10:00:00Z");

        assertThat(RequestExpiryPolicy.expiresAt(CREATED, start)).isEqualTo(Instant.parse("2099-06-03T10:00:00Z"));
    }

    @Test
    void expiresTwentyFourHoursBeforeTheStartWhenItComesFirst() {
        Instant start = Instant.parse("2099-06-03T10:00:00Z");

        assertThat(RequestExpiryPolicy.expiresAt(CREATED, start)).isEqualTo(Instant.parse("2099-06-02T10:00:00Z"));
    }

    @Test
    void leavesAtLeastFourHoursWhenTheStartIsLessThanADayAway() {
        Instant start = Instant.parse("2099-06-02T08:00:00Z");

        assertThat(RequestExpiryPolicy.expiresAt(CREATED, start)).isEqualTo(Instant.parse("2099-06-01T14:00:00Z"));
    }

    @Test
    void neverExpiresAfterTheStart() {
        Instant start = Instant.parse("2099-06-01T12:00:00Z");

        assertThat(RequestExpiryPolicy.expiresAt(CREATED, start)).isEqualTo(start);
    }

    @Test
    void isDueFromTheDeadlineIncluded() {
        Instant start = Instant.parse("2099-06-20T10:00:00Z");
        Instant deadline = Instant.parse("2099-06-03T10:00:00Z");

        assertThat(RequestExpiryPolicy.isDue(CREATED, start, deadline.minusSeconds(1))).isFalse();
        assertThat(RequestExpiryPolicy.isDue(CREATED, start, deadline)).isTrue();
        assertThat(RequestExpiryPolicy.isDue(CREATED, start, deadline.plusSeconds(1))).isTrue();
    }

    @Test
    void aRequestWhoseStartHasPassedIsDue() {
        Instant start = Instant.parse("2099-06-01T12:00:00Z");

        assertThat(RequestExpiryPolicy.isDue(CREATED, start, Instant.parse("2099-07-01T00:00:00Z"))).isTrue();
    }
}
