package com.apontaja.back.account.application;

import com.apontaja.back.account.domain.AccessTokenIssuer;
import com.apontaja.back.account.domain.OpaqueTokenGenerator;
import com.apontaja.back.account.domain.RefreshToken;
import com.apontaja.back.account.domain.RefreshTokenRepository;
import com.apontaja.back.account.domain.TokenHasher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AccessTokenIssuer accessTokenIssuer;

    @Mock
    private OpaqueTokenGenerator opaqueTokenGenerator;

    @Mock
    private TokenHasher tokenHasher;

    private RefreshTokenService service;

    private final Instant fixedNow = Instant.parse("2026-08-31T10:00:00Z");
    private final UUID accountId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(refreshTokenRepository, accessTokenIssuer, opaqueTokenGenerator, tokenHasher,
                () -> new UUID(0, 1), Clock.fixed(fixedNow, ZoneOffset.UTC), Duration.ofDays(30));
    }

    private RefreshToken activeToken() {
        return new RefreshToken(UUID.randomUUID(), accountId, "hashed-old", "device-1",
                fixedNow.plus(1, ChronoUnit.DAYS), fixedNow.minus(1, ChronoUnit.DAYS));
    }

    @Test
    void rotation_reussie_revoque_l_ancien_et_emet_un_nouveau_token() {
        when(tokenHasher.hash("raw-old")).thenReturn("hashed-old");
        RefreshToken oldToken = activeToken();
        when(refreshTokenRepository.findByTokenHash("hashed-old")).thenReturn(Optional.of(oldToken));
        when(opaqueTokenGenerator.generate()).thenReturn("raw-new");
        when(tokenHasher.hash("raw-new")).thenReturn("hashed-new");
        when(accessTokenIssuer.issue(accountId)).thenReturn("new-access-token");

        RefreshResult result = service.refresh("raw-old");

        assertThat(oldToken.isRevoked()).isTrue();
        assertThat(result.rawRefreshToken()).isEqualTo("raw-new");
        assertThat(result.accessToken()).isEqualTo("new-access-token");

        var captor = org.mockito.ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).anySatisfy(t -> assertThat(t.getTokenHash()).isEqualTo("hashed-new"));
    }

    @Test
    void reutilisation_d_un_token_deja_revoque_invalide_toute_la_famille() {
        RefreshToken reusedToken = activeToken();
        reusedToken.revoke(fixedNow.minus(1, ChronoUnit.HOURS));
        when(tokenHasher.hash("raw-stolen")).thenReturn("hashed-old");
        when(refreshTokenRepository.findByTokenHash("hashed-old")).thenReturn(Optional.of(reusedToken));

        RefreshToken otherActiveToken = new RefreshToken(UUID.randomUUID(), accountId, "hashed-other", "device-2",
                fixedNow.plus(1, ChronoUnit.DAYS), fixedNow.minus(1, ChronoUnit.DAYS));
        when(refreshTokenRepository.findActiveByAccountId(accountId)).thenReturn(List.of(otherActiveToken));

        assertThatThrownBy(() -> service.refresh("raw-stolen")).isInstanceOf(RefreshTokenReuseDetectedException.class);

        assertThat(otherActiveToken.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(otherActiveToken);
    }

    @Test
    void token_expire_est_rejete_sans_declencher_la_revocation_de_toute_la_famille() {
        RefreshToken expiredToken = new RefreshToken(UUID.randomUUID(), accountId, "hashed-expired", "device-1",
                fixedNow.minus(1, ChronoUnit.HOURS), fixedNow.minus(1, ChronoUnit.DAYS));
        when(tokenHasher.hash("raw-expired")).thenReturn("hashed-expired");
        when(refreshTokenRepository.findByTokenHash("hashed-expired")).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> service.refresh("raw-expired")).isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository, org.mockito.Mockito.never()).findActiveByAccountId(any());
    }

    @Test
    void token_inconnu_est_rejete() {
        when(tokenHasher.hash("inconnu")).thenReturn("hash-inconnu");
        when(refreshTokenRepository.findByTokenHash("hash-inconnu")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh("inconnu")).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logout_revoque_le_token_presente() {
        RefreshToken token = activeToken();
        when(tokenHasher.hash("raw-old")).thenReturn("hashed-old");
        when(refreshTokenRepository.findByTokenHash("hashed-old")).thenReturn(Optional.of(token));

        service.logout("raw-old");

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    @Test
    void logout_est_idempotent_sur_un_token_inconnu() {
        when(tokenHasher.hash("inconnu")).thenReturn("hash-inconnu");
        when(refreshTokenRepository.findByTokenHash("hash-inconnu")).thenReturn(Optional.empty());

        service.logout("inconnu"); // ne doit pas lever d'exception

        verify(refreshTokenRepository, org.mockito.Mockito.never()).save(any());
    }
}
