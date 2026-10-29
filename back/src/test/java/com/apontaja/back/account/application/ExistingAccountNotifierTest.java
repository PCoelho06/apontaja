package com.apontaja.back.account.application;

import com.apontaja.back.account.domain.Account;
import com.apontaja.back.shared.domain.EmailSender;
import com.apontaja.back.shared.domain.TokenHasher;

import com.github.benmanes.caffeine.cache.Ticker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExistingAccountNotifierTest {

    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final AtomicLong nanos = new AtomicLong();

    private EmailSender emailSender;

    private ExistingAccountNotifier notifier;

    @BeforeEach
    void setUp() {
        TokenHasher hasher = mock(TokenHasher.class);
        when(hasher.hash(anyString())).thenAnswer(invocation -> "h:" + invocation.getArgument(0));
        emailSender = mock(EmailSender.class);
        FrontendLinks links = new FrontendLinks("http://salon.test", "http://client.test");
        Ticker ticker = nanos::get;
        notifier = new ExistingAccountNotifier(hasher, emailSender, links, WINDOW, ticker);
    }

    private static Account account(String email) {
        return new Account(UUID.randomUUID(), email, "Alice", "Martin", "hash", Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void envoie_un_email_avec_les_liens_du_portail_demande() {
        notifier.notifyAccountExists(account("alice@example.com"), FrontendPortal.CLIENT);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(eq("alice@example.com"), eq("Já tem uma conta na Apontaja"), body.capture());
        assertThat(body.getValue()).contains("\nhttp://client.test/entrar\n")
                .contains("\nhttp://client.test/recuperar-palavra-passe\n").doesNotContain("token=");
    }

    @Test
    void sans_portail_les_liens_pointent_vers_le_salon() {
        notifier.notifyAccountExists(account("alice@example.com"), null);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(eq("alice@example.com"), anyString(), body.capture());
        assertThat(body.getValue()).contains("\nhttp://salon.test/entrar\n");
    }

    @Test
    void un_seul_email_par_adresse_dans_la_fenetre_sans_tenir_compte_de_la_casse() {
        notifier.notifyAccountExists(account("alice@example.com"), FrontendPortal.CLIENT);
        notifier.notifyAccountExists(account("Alice@Example.com"), FrontendPortal.CLIENT);
        notifier.notifyAccountExists(account("alice@example.com"), FrontendPortal.SALON);

        verify(emailSender, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    void une_autre_adresse_n_est_pas_freinee() {
        notifier.notifyAccountExists(account("alice@example.com"), FrontendPortal.CLIENT);
        notifier.notifyAccountExists(account("bob@example.com"), FrontendPortal.CLIENT);

        verify(emailSender).send(eq("alice@example.com"), anyString(), anyString());
        verify(emailSender).send(eq("bob@example.com"), anyString(), anyString());
    }

    @Test
    void renvoie_un_email_une_fois_la_fenetre_ecoulee() {
        notifier.notifyAccountExists(account("alice@example.com"), FrontendPortal.CLIENT);

        nanos.addAndGet(WINDOW.minusSeconds(1).toNanos());
        notifier.notifyAccountExists(account("alice@example.com"), FrontendPortal.CLIENT);
        verify(emailSender, times(1)).send(anyString(), anyString(), anyString());

        nanos.addAndGet(Duration.ofSeconds(2).toNanos());
        notifier.notifyAccountExists(account("alice@example.com"), FrontendPortal.CLIENT);
        verify(emailSender, times(2)).send(anyString(), anyString(), anyString());
    }
}
