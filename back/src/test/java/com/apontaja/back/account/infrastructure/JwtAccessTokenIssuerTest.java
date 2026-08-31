package com.apontaja.back.account.infrastructure;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAccessTokenIssuerTest {

    private static final String SECRET = "a-32-byte-minimum-test-secret-key-for-hs256!!";
    private final Instant fixedNow = Instant.parse("2026-08-29T10:00:00Z");

    @Test
    void round_trip_issue_puis_validate_retourne_le_meme_accountId() {
        JwtAccessTokenIssuer issuer = new JwtAccessTokenIssuer(
                SECRET, Duration.ofMinutes(15), Clock.fixed(fixedNow, ZoneOffset.UTC));
        UUID accountId = UUID.randomUUID();

        String token = issuer.issue(accountId);

        assertThat(issuer.validateAndExtractAccountId(token)).contains(accountId);
    }

    @Test
    void rejette_un_token_expire() {
        Clock issuedAt = Clock.fixed(fixedNow, ZoneOffset.UTC);
        JwtAccessTokenIssuer issuerAtIssueTime =
                new JwtAccessTokenIssuer(SECRET, Duration.ofMinutes(15), issuedAt);
        String token = issuerAtIssueTime.issue(UUID.randomUUID());

        Clock muchLater = Clock.fixed(fixedNow.plus(Duration.ofHours(1)), ZoneOffset.UTC);
        JwtAccessTokenIssuer issuerAtValidationTime =
                new JwtAccessTokenIssuer(SECRET, Duration.ofMinutes(15), muchLater);

        assertThat(issuerAtValidationTime.validateAndExtractAccountId(token)).isEmpty();
    }

    @Test
    void rejette_un_token_signe_avec_un_autre_secret() {
        JwtAccessTokenIssuer issuedWithSecretA = new JwtAccessTokenIssuer(
                SECRET, Duration.ofMinutes(15), Clock.fixed(fixedNow, ZoneOffset.UTC));
        String token = issuedWithSecretA.issue(UUID.randomUUID());

        JwtAccessTokenIssuer validatorWithSecretB = new JwtAccessTokenIssuer(
                "a-completely-different-32-byte-secret-key!!",
                Duration.ofMinutes(15),
                Clock.fixed(fixedNow, ZoneOffset.UTC));

        assertThat(validatorWithSecretB.validateAndExtractAccountId(token)).isEmpty();
    }

    @Test
    void rejette_une_chaine_qui_n_est_pas_un_jwt() {
        JwtAccessTokenIssuer issuer = new JwtAccessTokenIssuer(
                SECRET, Duration.ofMinutes(15), Clock.fixed(fixedNow, ZoneOffset.UTC));

        Optional<UUID> result = issuer.validateAndExtractAccountId("ceci-n-est-pas-un-jwt");

        assertThat(result).isEmpty();
    }
}
