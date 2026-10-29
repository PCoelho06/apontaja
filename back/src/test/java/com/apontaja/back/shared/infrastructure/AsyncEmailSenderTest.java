package com.apontaja.back.shared.infrastructure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsyncEmailSenderTest {

    @Mock
    private EmailTransport transport;

    /** Exécute dans le fil appelant : rend les tests déterministes. */
    private static final class DirectExecutorService extends AbstractExecutorService {

        private boolean shutdown;

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }

    private AsyncEmailSender direct() {
        return new AsyncEmailSender(transport, new DirectExecutorService());
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void hors_transaction_l_envoi_est_mis_en_file_immediatement() {
        direct().send("a@example.com", "Sujet", "Corps");

        verify(transport).send("a@example.com", "Sujet", "Corps");
    }

    @Test
    void dans_une_transaction_l_envoi_attend_la_validation() {
        TransactionSynchronizationManager.initSynchronization();

        direct().send("a@example.com", "Sujet", "Corps");

        verify(transport, never()).send(any(), any(), any());
        TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        verify(transport).send("a@example.com", "Sujet", "Corps");
    }

    @Test
    void un_rollback_n_envoie_rien() {
        TransactionSynchronizationManager.initSynchronization();

        direct().send("a@example.com", "Sujet", "Corps");
        TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(transport, never()).send(any(), any(), any());
    }

    @Test
    void un_echec_du_transport_n_est_jamais_propage() {
        doThrow(new IllegalStateException("smtp down")).when(transport).send(any(), any(), any());

        assertThatCode(() -> direct().send("a@example.com", "Sujet", "Corps")).doesNotThrowAnyException();
    }

    @Test
    void une_file_saturee_n_est_jamais_propagee() {
        ExecutorService full = mock(ExecutorService.class);
        doThrow(new RejectedExecutionException("full")).when(full).execute(any(Runnable.class));

        assertThatCode(() -> new AsyncEmailSender(transport, full).send("a@example.com", "Sujet", "Corps"))
                .doesNotThrowAnyException();

        verify(transport, never()).send(any(), any(), any());
    }

    @Test
    void l_executeur_par_defaut_envoie_dans_un_fil_dedie() throws Exception {
        AtomicReference<String> threadName = new AtomicReference<>();
        doAnswer(invocation -> {
            threadName.set(Thread.currentThread().getName());
            return null;
        }).when(transport).send(any(), any(), any());
        AsyncEmailSender sender = new AsyncEmailSender(transport);

        sender.send("a@example.com", "Sujet", "Corps");

        verify(transport, timeout(2000)).send("a@example.com", "Sujet", "Corps");
        sender.destroy();
        assertThat(threadName.get()).startsWith("email-sender-");
    }

    @Test
    void l_arret_laisse_partir_les_emails_en_file() throws Exception {
        ExecutorService executor = mock(ExecutorService.class);
        when(executor.awaitTermination(anyLong(), any())).thenReturn(true);

        new AsyncEmailSender(transport, executor).destroy();

        verify(executor).shutdown();
        verify(executor, never()).shutdownNow();
    }

    @Test
    void l_arret_force_l_interruption_apres_le_delai() throws Exception {
        ExecutorService executor = mock(ExecutorService.class);
        when(executor.awaitTermination(anyLong(), any())).thenReturn(false);

        new AsyncEmailSender(transport, executor).destroy();

        verify(executor).shutdownNow();
    }
}
