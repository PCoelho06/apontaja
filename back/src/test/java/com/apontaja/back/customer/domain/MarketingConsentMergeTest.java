package com.apontaja.back.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

class MarketingConsentMergeTest {

    private static final Instant T1 = Instant.parse("2020-01-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2020-02-01T00:00:00Z");
    private static final Instant T3 = Instant.parse("2020-03-01T00:00:00Z");

    private static SalonCustomerLink link() {
        return new SalonCustomerLink(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                CustomerLinkSource.MANUAL, null, T1);
    }

    @Test
    void noConsentAnywhereGivesNoConsent() {
        MarketingConsentMerge.Result result = MarketingConsentMerge.merge(List.of(link(), link()));

        assertThat(result.consentAt()).isNull();
        assertThat(result.revokedAt()).isNull();
    }

    @Test
    void latestRevocationBeatsOlderGrant() {
        SalonCustomerLink revoked = link();
        revoked.grantMarketingConsent(T1);
        revoked.revokeMarketingConsent(T3);
        SalonCustomerLink granted = link();
        granted.grantMarketingConsent(T2);

        MarketingConsentMerge.Result result = MarketingConsentMerge.merge(List.of(granted, revoked));

        assertThat(result.consentAt()).isEqualTo(T1);
        assertThat(result.revokedAt()).isEqualTo(T3);
    }

    @Test
    void latestGrantBeatsOlderRevocation() {
        SalonCustomerLink revoked = link();
        revoked.grantMarketingConsent(T1);
        revoked.revokeMarketingConsent(T2);
        SalonCustomerLink granted = link();
        granted.grantMarketingConsent(T3);

        MarketingConsentMerge.Result result = MarketingConsentMerge.merge(List.of(revoked, granted));

        assertThat(result.consentAt()).isEqualTo(T3);
        assertThat(result.revokedAt()).isNull();
    }

    @Test
    void revocationWinsOnTie() {
        SalonCustomerLink revoked = link();
        revoked.grantMarketingConsent(T1);
        revoked.revokeMarketingConsent(T2);
        SalonCustomerLink granted = link();
        granted.grantMarketingConsent(T2);

        MarketingConsentMerge.Result result = MarketingConsentMerge.merge(List.of(granted, revoked));

        assertThat(result.revokedAt()).isEqualTo(T2);
    }
}
