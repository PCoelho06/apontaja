package com.apontaja.back.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

class DuplicateDetectorTest {

    private static final Instant T0 = Instant.parse("2020-01-01T00:00:00Z");

    private static DuplicateCandidate candidate(long id, String email, String phone, long dayOffset) {
        return new DuplicateCandidate(new UUID(0, id), "Prénom" + id, "Nom" + id, email, phone, null,
                T0.plusSeconds(dayOffset * 86_400));
    }

    @Test
    void groupsSameEmailIgnoringCaseAndSpaces() {
        List<DuplicateDetector.Cluster> clusters = DuplicateDetector.detect(List.of(
                candidate(1, "Marie@Example.fr", null, 0), candidate(2, " marie@example.FR ", null, 1),
                candidate(3, "autre@example.fr", null, 2)));

        assertThat(clusters).hasSize(1);
        assertThat(clusters.get(0).reasons()).containsExactly(DuplicateReason.EMAIL);
        assertThat(clusters.get(0).members()).extracting(DuplicateCandidate::profileId)
                .containsExactly(new UUID(0, 1), new UUID(0, 2));
    }

    @Test
    void groupsNationalAndInternationalPhoneFormats() {
        List<DuplicateDetector.Cluster> clusters = DuplicateDetector.detect(List.of(
                candidate(1, null, "06 12 34 56 78", 0), candidate(2, null, "+33 6 12 34 56 78", 1),
                candidate(3, null, "0033612345678", 2)));

        assertThat(clusters).hasSize(1);
        assertThat(clusters.get(0).reasons()).containsExactly(DuplicateReason.PHONE);
        assertThat(clusters.get(0).members()).hasSize(3);
    }

    @Test
    void groupsAreTransitiveAndCarryBothReasons() {
        List<DuplicateDetector.Cluster> clusters = DuplicateDetector.detect(List.of(
                candidate(1, "a@example.fr", null, 0), candidate(2, "a@example.fr", "0611111111", 1),
                candidate(3, null, "06 11 11 11 11", 2)));

        assertThat(clusters).hasSize(1);
        assertThat(clusters.get(0).reasons()).containsExactlyInAnyOrder(DuplicateReason.EMAIL,
                DuplicateReason.PHONE);
        assertThat(clusters.get(0).members()).hasSize(3);
    }

    @Test
    void ignoresTooShortPhonesAndMissingContacts() {
        List<DuplicateDetector.Cluster> clusters = DuplicateDetector.detect(List.of(
                candidate(1, null, "1234567", 0), candidate(2, null, "1234567", 1),
                candidate(3, "x@example.fr", null, 2), candidate(4, null, null, 3)));

        assertThat(clusters).isEmpty();
    }

    @Test
    void keepsDistinctGroupsSeparateAndOrdersOldestFirst() {
        List<DuplicateDetector.Cluster> clusters = DuplicateDetector.detect(List.of(
                candidate(1, "b@example.fr", null, 5), candidate(2, "a@example.fr", null, 1),
                candidate(3, "b@example.fr", null, 6), candidate(4, "a@example.fr", null, 2)));

        assertThat(clusters).hasSize(2);
        assertThat(clusters.get(0).members().get(0).profileId()).isEqualTo(new UUID(0, 2));
        assertThat(clusters.get(1).members().get(0).profileId()).isEqualTo(new UUID(0, 1));
    }
}
