package com.apontaja.back.account.application;

import com.apontaja.back.account.domain.Account;
import com.apontaja.back.account.domain.AccountRepository;
import com.apontaja.back.account.domain.AccountToken;
import com.apontaja.back.account.domain.AccountTokenRepository;
import com.apontaja.back.account.domain.AccountTokenType;
import com.apontaja.back.account.domain.EmailSender;
import com.apontaja.back.account.domain.OpaqueTokenGenerator;
import com.apontaja.back.account.domain.RefreshToken;
import com.apontaja.back.account.domain.RefreshTokenRepository;
import com.apontaja.back.account.domain.TokenHasher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

        @Mock
        private AccountRepository accountRepository;

        @Mock
        private AccountTokenRepository accountTokenRepository;

        @Mock
        private RefreshTokenRepository refreshTokenRepository;

        @Mock
        private OpaqueTokenGenerator opaqueTokenGenerator;

        @Mock
        private TokenHasher tokenHasher;

        @Mock
        private PasswordEncoder passwordEncoder;

        @Mock
        private EmailSender emailSender;

        private PasswordResetService service;

        private final Instant fixedNow = Instant.parse("2026-08-31T10:00:00Z");
        private final UUID accountId = UUID.randomUUID();

        @BeforeEach
        void setUp() {
                service = new PasswordResetService(accountRepository, accountTokenRepository, refreshTokenRepository,
                                opaqueTokenGenerator, tokenHasher, passwordEncoder, emailSender, () -> new UUID(0, 1),
                                Clock.fixed(fixedNow, ZoneOffset.UTC), Duration.ofHours(1), "http://localhost:5173");
        }

        @Test
        void requestReset_ne_fait_rien_silencieusement_si_email_inconnu() {
                when(accountRepository.findAliveByEmail("inconnu@example.com")).thenReturn(Optional.empty());

                service.requestReset("inconnu@example.com");

                verify(accountTokenRepository, never()).save(org.mockito.ArgumentMatchers.any());
                verify(emailSender, never()).send(anyString(), anyString(), anyString());
        }

        @Test
        void requestReset_cree_un_token_et_envoie_le_lien_si_le_compte_existe() {
                Account account = new Account(accountId, "bob@example.com", "hash", fixedNow);
                when(accountRepository.findAliveByEmail("bob@example.com")).thenReturn(Optional.of(account));
                when(opaqueTokenGenerator.generate()).thenReturn("raw-token");
                when(tokenHasher.hash("raw-token")).thenReturn("hashed-token");

                service.requestReset("bob@example.com");

                verify(accountTokenRepository).save(org.mockito.ArgumentMatchers
                                .argThat(token -> token.getType() == AccountTokenType.PASSWORD_RESET
                                                && token.getTokenHash().equals("hashed-token")));
                verify(emailSender).send(org.mockito.ArgumentMatchers.eq("bob@example.com"), anyString(),
                                org.mockito.ArgumentMatchers.contains("raw-token"));
        }

        @Test
        void resetPassword_change_le_mot_de_passe_et_revoque_toutes_les_sessions_actives() {
                Account account = new Account(accountId, "carol@example.com", "old-hash",
                                fixedNow.minus(1, ChronoUnit.DAYS));
                AccountToken token = new AccountToken(UUID.randomUUID(), accountId, AccountTokenType.PASSWORD_RESET,
                                "hashed-token", fixedNow.plus(1, ChronoUnit.HOURS),
                                fixedNow.minus(10, ChronoUnit.MINUTES));
                when(tokenHasher.hash("raw-token")).thenReturn("hashed-token");
                when(accountTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(token));
                when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
                when(passwordEncoder.encode("nouveau-mot-de-passe-suffisant")).thenReturn("new-hash");

                RefreshToken activeSession = new RefreshToken(UUID.randomUUID(), accountId, "session-hash", "device-1",
                                fixedNow.plus(30, ChronoUnit.DAYS), fixedNow.minus(1, ChronoUnit.DAYS));
                when(refreshTokenRepository.findActiveByAccountId(accountId)).thenReturn(List.of(activeSession));

                service.resetPassword("raw-token", "nouveau-mot-de-passe-suffisant");

                assertThat(account.getPasswordHash()).isEqualTo("new-hash");
                assertThat(token.isUsed()).isTrue();
                assertThat(activeSession.isRevoked()).isTrue();
                verify(refreshTokenRepository).save(activeSession);
        }

        @Test
        void resetPassword_rejette_un_token_deja_utilise() {
                AccountToken usedToken = new AccountToken(UUID.randomUUID(), accountId, AccountTokenType.PASSWORD_RESET,
                                "hashed-used", fixedNow.plus(1, ChronoUnit.HOURS),
                                fixedNow.minus(10, ChronoUnit.MINUTES));
                usedToken.markUsed(fixedNow.minus(5, ChronoUnit.MINUTES));
                when(tokenHasher.hash("raw-used")).thenReturn("hashed-used");
                when(accountTokenRepository.findByTokenHash("hashed-used")).thenReturn(Optional.of(usedToken));

                assertThatThrownBy(() -> service.resetPassword("raw-used", "nouveau-mot-de-passe-suffisant"))
                                .isInstanceOf(InvalidOrExpiredTokenException.class);
        }
}
