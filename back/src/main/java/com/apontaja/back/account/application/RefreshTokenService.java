package com.apontaja.back.account.application;

import com.apontaja.back.account.domain.AccessTokenIssuer;
import com.apontaja.back.account.domain.IdGenerator;
import com.apontaja.back.account.domain.OpaqueTokenGenerator;
import com.apontaja.back.account.domain.RefreshToken;
import com.apontaja.back.account.domain.RefreshTokenRepository;
import com.apontaja.back.account.domain.TokenHasher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenIssuer accessTokenIssuer;
    private final OpaqueTokenGenerator opaqueTokenGenerator;
    private final TokenHasher tokenHasher;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final Duration refreshTokenTtl;

    RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            AccessTokenIssuer accessTokenIssuer,
            OpaqueTokenGenerator opaqueTokenGenerator,
            TokenHasher tokenHasher,
            IdGenerator idGenerator,
            Clock clock,
            @Value("${apontaja.security.refresh-token.ttl:30d}") Duration refreshTokenTtl) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.accessTokenIssuer = accessTokenIssuer;
        this.opaqueTokenGenerator = opaqueTokenGenerator;
        this.tokenHasher = tokenHasher;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    /**
     * Rotation à chaque appel : l'ancien token est révoqué, un nouveau est
     * émis. Si le token présenté est déjà révoqué (donc déjà utilisé une
     * fois), c'est un signal de vol probable — toute la famille de tokens
     * du compte est invalidée (granularité compte, pas device, cf. note de
     * tranche 5 dans le contexte projet).
     */
    @Transactional(noRollbackFor = RefreshTokenReuseDetectedException.class)
    public RefreshResult refresh(String rawRefreshToken) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHasher.hash(rawRefreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        Instant now = clock.instant();

        if (token.isRevoked()) {
            log.warn(
                    "Réutilisation détectée d'un refresh token déjà révoqué (accountId={}) "
                            + "— invalidation de toute la famille de tokens.",
                    token.getAccountId());
            revokeAllActiveForAccount(token.getAccountId(), now);
            throw new RefreshTokenReuseDetectedException();
        }

        if (token.isExpired(now)) {
            throw new InvalidRefreshTokenException();
        }

        token.revoke(now);
        refreshTokenRepository.save(token);

        String newRawRefreshToken = opaqueTokenGenerator.generate();
        Instant expiresAt = now.plus(refreshTokenTtl);
        RefreshToken newToken = new RefreshToken(
                idGenerator.generate(),
                token.getAccountId(),
                tokenHasher.hash(newRawRefreshToken),
                token.getDeviceInfo(),
                expiresAt,
                now);
        refreshTokenRepository.save(newToken);

        String accessToken = accessTokenIssuer.issue(token.getAccountId());

        return new RefreshResult(token.getAccountId(), accessToken, newRawRefreshToken, expiresAt);
    }

    /**
     * Idempotent : pas d'erreur si le token est inconnu ou déjà révoqué — le but
     * (ne plus être authentifié) est atteint dans tous les cas.
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(tokenHasher.hash(rawRefreshToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> {
                    token.revoke(clock.instant());
                    refreshTokenRepository.save(token);
                });
    }

    private void revokeAllActiveForAccount(UUID accountId, Instant now) {
        refreshTokenRepository.findActiveByAccountId(accountId)
                .forEach(token -> {
                    token.revoke(now);
                    refreshTokenRepository.save(token);
                });
    }
}
