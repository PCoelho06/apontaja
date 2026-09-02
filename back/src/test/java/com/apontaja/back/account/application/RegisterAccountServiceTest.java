package com.apontaja.back.account.application;

import com.apontaja.back.account.domain.Account;
import com.apontaja.back.account.domain.AccountRepository;
import com.apontaja.back.account.domain.ConsentRecord;
import com.apontaja.back.account.domain.ConsentRecordRepository;
import com.apontaja.back.account.domain.ConsentType;
import com.apontaja.back.account.domain.IdGenerator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterAccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ConsentRecordRepository consentRecordRepository;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private RegisterAccountService service;

    private final Instant fixedNow = Instant.parse("2026-08-29T10:00:00Z");

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);
        service = new RegisterAccountService(
                accountRepository, consentRecordRepository, emailVerificationService, passwordEncoder,
                new SequentialTestIdGenerator(), clock);
    }

    @Test
    void enregistre_le_compte_et_les_deux_consentements_obligatoires() {
        when(accountRepository.existsAliveByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("un-mot-de-passe-suffisamment-long")).thenReturn("hashed");
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegisterAccountResult result = service.register(
                new RegisterAccountCommand("alice@example.com", "un-mot-de-passe-suffisamment-long"));

        assertThat(result.email()).isEqualTo("alice@example.com");

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getPasswordHash()).isEqualTo("hashed");

        ArgumentCaptor<ConsentRecord> consentCaptor = ArgumentCaptor.forClass(ConsentRecord.class);
        verify(consentRecordRepository, Mockito.times(2)).save(consentCaptor.capture());
        List<ConsentType> types = consentCaptor.getAllValues().stream().map(ConsentRecord::getType).toList();
        assertThat(types).containsExactlyInAnyOrder(ConsentType.TOS, ConsentType.PRIVACY);

        verify(emailVerificationService).issueAndSend(any(Account.class));
    }

    @Test
    void rejette_l_inscription_si_l_email_est_deja_utilise() {
        when(accountRepository.existsAliveByEmail("bob@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(
                new RegisterAccountCommand("bob@example.com", "un-mot-de-passe-suffisamment-long")))
                .isInstanceOf(EmailAlreadyUsedException.class);
    }

    private static final class SequentialTestIdGenerator implements IdGenerator {
        private int counter = 0;

        @Override
        public UUID generate() {
            counter++;
            return new UUID(0, counter);
        }
    }
}
