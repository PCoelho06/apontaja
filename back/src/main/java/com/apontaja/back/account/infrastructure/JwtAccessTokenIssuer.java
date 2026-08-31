package com.apontaja.back.account.infrastructure;

import com.apontaja.back.account.domain.AccessTokenIssuer;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * HS256 : suffisant tant qu'un seul service (ce backend) émet et valide les
 * tokens. À revoir vers RS256/EdDSA le jour où un autre service doit
 * valider sans connaître le secret.
 *
 * <p>{@code apontaja.security.jwt.secret} doit être une chaîne aléatoire
 * d'au moins 32 octets (256 bits) — voir application-local.yml (dev local,
 * jamais commité). Ex. génération : {@code openssl rand -base64 48}.
 */
@Component
class JwtAccessTokenIssuer implements AccessTokenIssuer {

    private final SecretKey signingKey;
    private final Duration accessTokenTtl;
    private final Clock clock;

    JwtAccessTokenIssuer(
            @Value("${apontaja.security.jwt.secret}") String secret,
            // Si le binding Duration échoue sur votre version de Boot,
            // remplacez par un @Value String + Duration.parse("PT15M") manuel.
            @Value("${apontaja.security.jwt.access-token-ttl:15m}") Duration accessTokenTtl,
            Clock clock) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = accessTokenTtl;
        this.clock = clock;
    }

    @Override
    public String issue(UUID accountId) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(accountId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(signingKey)
                .compact();
    }

    /** Utilisé directement par JwtAuthenticationFilter — pas exposé au domaine. */
    Optional<UUID> validateAndExtractAccountId(String token) {
        try {
            Claims claims = Jwts.parser()
                    // Sans ce .clock(...), jjwt valide l'expiration avec
                    // Clock.systemUTC() en interne, indépendamment du Clock
                    // injecté dans ce constructeur — piège découvert via un
                    // test utilisant un Clock figé dans le passé.
                    .clock(() -> Date.from(clock.instant()))
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(UUID.fromString(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
