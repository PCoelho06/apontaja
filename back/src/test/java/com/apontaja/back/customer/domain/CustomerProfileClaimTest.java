package com.apontaja.back.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

class CustomerProfileClaimTest {

    private static final Instant T0 = Instant.parse("2020-01-01T10:00:00Z");

    private static CustomerProfile profile() {
        return new CustomerProfile(UUID.randomUUID(), "Alice", "Martin", "alice@example.com", null, T0);
    }

    @Test
    void claimBy_rattache_le_compte_sans_toucher_a_l_identite() {
        CustomerProfile profile = profile();
        UUID accountId = UUID.randomUUID();

        profile.claimBy(accountId);

        assertThat(profile.getAccountId()).isEqualTo(accountId);
        assertThat(profile.getFirstName()).isEqualTo("Alice");
        assertThat(profile.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void claimBy_refuse_un_compte_nul() {
        assertThatThrownBy(() -> profile().claimBy(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void l_anonymisation_detache_le_compte() {
        CustomerProfile profile = profile();
        profile.claimBy(UUID.randomUUID());

        profile.anonymize(T0);

        assertThat(profile.getAccountId()).isNull();
    }
}
