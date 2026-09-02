package com.apontaja.back.account.application;

import com.apontaja.back.account.domain.Account;
import com.apontaja.back.account.domain.AccountRepository;
import com.apontaja.back.account.domain.AccountToken;
import com.apontaja.back.account.domain.AccountTokenRepository;
import com.apontaja.back.account.domain.AccountTokenType;
import com.apontaja.back.account.domain.EmailSender;
import com.apontaja.back.account.domain.OpaqueTokenGenerator;
import com.apontaja.back.account.domain.TokenHasher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountTokenRepository accountTokenRepository;

    @Mock
    private OpaqueTokenGenerator opaqueTokenGenerator;

    @Mock
    private TokenHasher tokenHasher;

    @Mock
    private EmailSender emailSender;

    private EmailVerificationService service;

    private final Instant fixedNow = Instant.parse("2026-08-31T10:00:00Z");
    private final UUID accountId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(accountRepository, accountTokenRepository, opaqueTokenGenerator,
                tokenHasher, emailSender, () -> new UUID(0, 1), Clock.fixed(fixedNow, ZoneOffset.UTC),
                Duration.ofHours(48), "http://localhost:5173");
    }

    private Account unverifiedAccount() {
        return new Account(accountId, "alice@example.com", "hash", fixedNow);
    }

    @Test
    void issueAndSend_cree_le_token_et_envoie_le_lien() {
        Account account = unverifiedAccount();
        when(opaqueTokenGenerator.generate()).thenReturn("raw-token");
        when(tokenHasher.hash("raw-token")).thenReturn("hashed-token");

        service.issueAndSend(account);

        ArgumentCaptor<AccountToken> captor = ArgumentCaptor.forClass(AccountToken.class);
        verify(accountTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(AccountTokenType.EMAIL_VERIFICATION);
        assertThat(captor.getValue().getTokenHash()).isEqualTo("hashed-token");

        verify(emailSender).send(eq("alice@example.com"), anyString(),
                org.mockito.ArgumentMatchers.contains("raw-token"));
    }

    @Test
    void confirm_marque_le_compte_verifie_et_le_token_utilise() {
        AccountToken token = new AccountToken(UUID.randomUUID(), accountId, AccountTokenType.EMAIL_VERIFICATION,
                "hashed-token", fixedNow.plus(1, ChronoUnit.DAYS), fixedNow.minus(1, ChronoUnit.HOURS));
        when(tokenHasher.hash("raw-token")).thenReturn("hashed-token");
        when(accountTokenRepository.findByTokenHash("hashed-token")).thenReturn(Optional.of(token));
        Account account = unverifiedAccount();
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        service.confirm("raw-token");

        assertThat(account.isEmailVerified()).isTrue();
        assertThat(token.isUsed()).isTrue();
        verify(accountRepository).save(account);
        verify(accountTokenRepository).save(token);
    }

    @Test
    void confirm_rejette_un_token_expire() {
        AccountToken expiredToken = new AccountToken(UUID.randomUUID(), accountId, AccountTokenType.EMAIL_VERIFICATION,
                "hashed-expired", fixedNow.minus(1, ChronoUnit.HOURS), fixedNow.minus(2, ChronoUnit.DAYS));
        when(tokenHasher.hash("raw-expired")).thenReturn("hashed-expired");
        when(accountTokenRepository.findByTokenHash("hashed-expired")).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> service.confirm("raw-expired")).isInstanceOf(InvalidOrExpiredTokenException.class);
    }

    @Test
    void confirm_rejette_un_token_du_mauvais_type() {
        AccountToken passwordResetToken = new AccountToken(UUID.randomUUID(), accountId,
                AccountTokenType.PASSWORD_RESET, "hashed-other", fixedNow.plus(1, ChronoUnit.DAYS), fixedNow);
        when(tokenHasher.hash("raw-other")).thenReturn("hashed-other");
        when(accountTokenRepository.findByTokenHash("hashed-other")).thenReturn(Optional.of(passwordResetToken));

        assertThatThrownBy(() -> service.confirm("raw-other")).isInstanceOf(InvalidOrExpiredTokenException.class);
    }

    @Test
    void resend_ne_fait_rien_silencieusement_si_email_inconnu() {
        when(accountRepository.findAliveByEmail("inconnu@example.com")).thenReturn(Optional.empty());

        service.resend("inconnu@example.com");

        verify(accountTokenRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void resend_ne_fait_rien_silencieusement_si_deja_verifie() {
        Account verified = unverifiedAccount();
        verified.markEmailVerified(fixedNow.minus(1, ChronoUnit.DAYS));
        when(accountRepository.findAliveByEmail("alice@example.com")).thenReturn(Optional.of(verified));

        service.resend("alice@example.com");

        verify(accountTokenRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
