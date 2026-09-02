package com.apontaja.back.account.application;

import com.apontaja.back.account.domain.Account;
import com.apontaja.back.account.domain.AccessTokenIssuer;
import com.apontaja.back.account.domain.AccountRepository;
import com.apontaja.back.account.domain.OpaqueTokenGenerator;
import com.apontaja.back.account.domain.RefreshToken;
import com.apontaja.back.account.domain.RefreshTokenRepository;
import com.apontaja.back.account.domain.TokenHasher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AccessTokenIssuer accessTokenIssuer;

    @Mock
    private OpaqueTokenGenerator opaqueTokenGenerator;

    @Mock
    private TokenHasher tokenHasher;

    private LoginService service;

    private final Instant fixedNow = Instant.parse("2026-08-29T10:00:00Z");
    private final UUID accountId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // encode() est appelé une fois à la construction pour le hash factice.
        when(passwordEncoder.encode(anyString())).thenReturn("dummy-hash");

        service = new LoginService(accountRepository, refreshTokenRepository, passwordEncoder, accessTokenIssuer,
                opaqueTokenGenerator, tokenHasher, () -> new UUID(0, 1), Clock.fixed(fixedNow, ZoneOffset.UTC),
                Duration.ofDays(30));
    }

    private Account existingAccount() {
        return new Account(accountId, "alice@example.com", "real-hash", fixedNow);
    }

    @Test
    void connexion_reussie_emet_access_token_et_persiste_le_refresh_token() {
        when(accountRepository.findAliveByEmail("alice@example.com")).thenReturn(Optional.of(existingAccount()));
        when(passwordEncoder.matches("bon-mot-de-passe", "real-hash")).thenReturn(true);
        when(accessTokenIssuer.issue(accountId)).thenReturn("jwt-access-token");
        when(opaqueTokenGenerator.generate()).thenReturn("raw-refresh-token");
        when(tokenHasher.hash("raw-refresh-token")).thenReturn("hashed-refresh-token");

        LoginResult result = service.login(new LoginCommand("alice@example.com", "bon-mot-de-passe", "device-1"));

        assertThat(result.accessToken()).isEqualTo("jwt-access-token");
        assertThat(result.rawRefreshToken()).isEqualTo("raw-refresh-token");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isEqualTo("hashed-refresh-token");
        assertThat(captor.getValue().getAccountId()).isEqualTo(accountId);
    }

    @Test
    void rejette_avec_mauvais_mot_de_passe() {
        when(accountRepository.findAliveByEmail("alice@example.com")).thenReturn(Optional.of(existingAccount()));
        when(passwordEncoder.matches("mauvais", "real-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginCommand("alice@example.com", "mauvais", null)))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(accessTokenIssuer, refreshTokenRepository);
    }

    @Test
    void rejette_avec_email_inconnu_en_appelant_quand_meme_passwordEncoder_matches() {
        when(accountRepository.findAliveByEmail("inconnu@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginCommand("inconnu@example.com", "peu-importe", null)))
                .isInstanceOf(InvalidCredentialsException.class);

        // Le point important : matches() est bien appelé même sans compte trouvé
        // (protection anti-timing), avec le hash factice comme second argument.
        verify(passwordEncoder).matches("peu-importe", "dummy-hash");
        verifyNoInteractions(accessTokenIssuer, refreshTokenRepository);
    }
}
