package com.apontaja.back.shared.infrastructure;

import com.apontaja.back.shared.domain.EmailSender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Seule implémentation du port {@link EmailSender} : tout envoi est <b>après commit</b> et <b>asynchrone</b>.
 *
 * <ul>
 * <li>Appelé dans une transaction : l'envoi n'est mis en file qu'une fois la transaction <i>validée</i> (jamais sur
 * un rollback : pas de lien vers un jeton qui n'existe pas). On s'accroche à {@code afterCompletion} et non à
 * {@code afterCommit} : une synchronisation enregistrée depuis un écouteur {@code AFTER_COMMIT} (déjà en phase de
 * validation) ne serait jamais appelée par {@code afterCommit}, alors qu'{@code afterCompletion} l'est dans les deux
 * cas.</li>
 * <li>Appelé hors transaction : mise en file immédiate.</li>
 * <li>L'envoi lui-même ({@link EmailTransport}) s'exécute sur un pool borné (2 fils, file de 500). File saturée ou
 * arrêtée : l'email est abandonné avec un avertissement, l'appelant n'est jamais touché. Pas de file persistante ni
 * de nouvelle tentative en v1.</li>
 * </ul>
 *
 * Tout échec est journalisé (sujet et type d'exception seulement, jamais l'adresse du destinataire) sans jamais être
 * propagé : l'action métier est déjà enregistrée.
 */
@Component
class AsyncEmailSender implements EmailSender, DisposableBean {

    static final int POOL_SIZE = 2;
    static final int QUEUE_CAPACITY = 500;
    static final int SHUTDOWN_WAIT_SECONDS = 10;

    private static final Logger log = LoggerFactory.getLogger(AsyncEmailSender.class);

    private final EmailTransport transport;
    private final ExecutorService executor;

    @Autowired
    AsyncEmailSender(EmailTransport transport) {
        this(transport, newBoundedExecutor());
    }

    AsyncEmailSender(EmailTransport transport, ExecutorService executor) {
        this.transport = transport;
        this.executor = executor;
    }

    @Override
    public void send(String to, String subject, String body) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_COMMITTED) {
                        enqueue(to, subject, body);
                    }
                }
            });
        } else {
            enqueue(to, subject, body);
        }
    }

    private void enqueue(String to, String subject, String body) {
        try {
            executor.execute(() -> deliver(to, subject, body));
        } catch (RejectedExecutionException e) {
            log.warn("Email non envoyé : file d'envoi saturée ou arrêtée (sujet : {}).", subject);
        }
    }

    private void deliver(String to, String subject, String body) {
        try {
            transport.send(to, subject, body);
        } catch (RuntimeException e) {
            log.warn("Échec de l'envoi d'un email (sujet : {}) : {}", subject, e.getClass().getName());
            log.debug("Détail de l'échec d'envoi", e);
        }
    }

    /** Laisse aux emails déjà en file le temps de partir avant l'arrêt de l'application. */
    @Override
    public void destroy() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static ExecutorService newBoundedExecutor() {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "email-sender-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(POOL_SIZE, POOL_SIZE, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY), factory);
    }
}
