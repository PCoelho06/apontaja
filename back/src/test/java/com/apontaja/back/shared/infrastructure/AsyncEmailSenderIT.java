package com.apontaja.back.shared.infrastructure;

import com.apontaja.back.shared.domain.EmailSender;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Vérifie avec le vrai gestionnaire de transactions que l'envoi attend la validation, y compris lorsqu'il est demandé
 * depuis un écouteur {@code AFTER_COMMIT} (cas de {@code ClientAppointmentEmailListener}). Le transport est simulé :
 * le port {@link EmailSender} réel ({@link AsyncEmailSender}) est celui qui est testé.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "apontaja.security.rate-limiting.enabled=false")
@Import({ PostgresTestcontainersConfiguration.class, AsyncEmailSenderIT.AfterCommitProbe.class })
class AsyncEmailSenderIT {

    /** Événement de test, volontairement sans lien avec le métier. */
    public record Ping(String id) {
    }

    /** Écouteur de test (enregistré par @Import, sans @Component : il ne doit pas être scanné par les autres ITs). */
    public static class AfterCommitProbe {

        private final EmailSender emailSender;

        AfterCommitProbe(EmailSender emailSender) {
            this.emailSender = emailSender;
        }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void onPing(Ping ping) {
            emailSender.send("probe@example.com", "Sujet " + ping.id(), "Corps");
        }
    }

    @MockitoBean
    private EmailTransport transport;

    @Autowired
    private EmailSender emailSender;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ApplicationEventPublisher publisher;

    @Test
    void l_envoi_demande_dans_une_transaction_part_apres_la_validation() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            emailSender.send("a@example.com", "Sujet A", "Corps");
            verify(transport, never()).send(any(), any(), any());
        });

        verify(transport, timeout(2000)).send("a@example.com", "Sujet A", "Corps");
    }

    @Test
    void un_rollback_n_envoie_rien() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            emailSender.send("b@example.com", "Sujet B", "Corps");
            status.setRollbackOnly();
        });

        verify(transport, after(500).never()).send(any(), any(), any());
    }

    @Test
    void l_envoi_demande_depuis_un_ecouteur_after_commit_part_bien() {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> publisher.publishEvent(new Ping("C")));

        verify(transport, timeout(2000)).send("probe@example.com", "Sujet C", "Corps");
    }

    @Test
    void l_envoi_hors_transaction_part_immediatement() {
        emailSender.send("d@example.com", "Sujet D", "Corps");

        verify(transport, timeout(2000)).send("d@example.com", "Sujet D", "Corps");
    }
}
