package com.apontaja.back.customer.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

class DuplicateReportMemberTest {

    private static final Instant T0 = Instant.parse("2020-01-01T10:00:00Z");

    private static DuplicateReport.Member member(String notMergeableReason) {
        return new DuplicateReport.Member(UUID.randomUUID(), "Alice", "Martin", "a@example.fr", null, T0, null, null,
                notMergeableReason);
    }

    @Test
    void claimed_n_est_vrai_que_pour_un_profil_reclame() {
        assertThat(member("CLAIMED").claimed()).isTrue();
        assertThat(member("SHARED").claimed()).isFalse();
        assertThat(member(null).claimed()).isFalse();
    }

    @Test
    void un_profil_reclame_reste_non_absorbable() {
        assertThat(member("CLAIMED").mergeable()).isFalse();
    }
}
