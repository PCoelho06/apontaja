package com.apontaja.back.account.application;

import com.apontaja.back.account.domain.Account;
import com.apontaja.back.account.domain.AccountRepository;
import com.apontaja.back.account.domain.ConsentRecord;
import com.apontaja.back.account.domain.ConsentRecordRepository;
import com.apontaja.back.account.domain.ConsentType;
import com.apontaja.back.shared.domain.IdGenerator;

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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterAccountServiceTest {

    private static final String PASSWORD = "un-mot-de-passe-suffisamment-long";

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ConsentRecordRepository consentRecordRepository;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ExistingAccountNotifier existingAccountNotifier;

    private RegisterAccountService service;

    private final Instant fixedNow = Instant.parse("2026-08-29T10:00:00Z");

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);
        service = new RegisterAccountService(accountRepository, consentRecordRepository, emailVerificationService,
                passwordEncoder, new SequentialTestIdGenerator(), clock, existingAccountNotifier);
    }

    @Test
    void enregistre_le_compte_et_les_deux_consentements_obligatoires() {
        when(accountRepository.findAliveByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed");
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.register(new RegisterAccountCommand("alice@example.com", "Alice", "Martin", PASSWORD,
                FrontendPortal.CLIENT));

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(accountCaptor.getValue().getPasswordHash()).isEqualTo("hashed");

        ArgumentCaptor<ConsentRecord> consentCaptor = ArgumentCaptor.forClass(ConsentRecord.class);
        verify(consentRecordRepository, Mockito.times(2)).save(consentCaptor.capture());
        List<ConsentType> types = consentCaptor.getAllValues().stream().map(ConsentRecord::getType).toList();
        assertThat(types).containsExactlyInAnyOrder(ConsentType.TOS, ConsentType.PRIVACY);

        verify(emailVerificationService).issueAndSend(any(Account.class), eq(FrontendPortal.CLIENT));
        verifyNoInteractions(existingAccountNotifier);
    }

    @Test
    void adresse_deja_utilisee_n_ecrit_rien_et_previent_le_titulaire() {
        Account existing = new Account(new UUID(1, 1), "bob@example.com", "Bob", "Dupont", "old-hash",
                Instant.parse("2026-01-01T00:00:00Z"));
        when(accountRepository.findAliveByEmail("bob@example.com")).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed");

        service.register(new RegisterAccountCommand("  bob@example.com ", "Intrus", "Autre", PASSWORD,
                FrontendPortal.SALON));

        verify(existingAccountNotifier).notifyAccountExists(existing, FrontendPortal.SALON);
        verify(accountRepository, never()).save(any(Account.class));
        verifyNoInteractions(consentRecordRepository, emailVerificationService);
    }

    @Test
    void le_mot_de_passe_est_hache_meme_si_l_adresse_existe_deja() {
        Account existing = new Account(new UUID(1, 1), "bob@example.com", "Bob", "Dupont", "old-hash",
                Instant.parse("2026-01-01T00:00:00Z"));
        when(accountRepository.findAliveByEmail("bob@example.com")).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed");

        service.register(new RegisterAccountCommand("bob@example.com", "Bob", "Dupont", PASSWORD, null));

        verify(passwordEncoder).encode(PASSWORD);
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
