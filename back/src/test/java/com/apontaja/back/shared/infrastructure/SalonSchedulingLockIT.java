package com.apontaja.back.shared.infrastructure;

import com.apontaja.back.shared.domain.SalonSchedulingLock;
import com.apontaja.back.testsupport.PostgresTestcontainersConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "apontaja.security.rate-limiting.enabled=false")
@Import(PostgresTestcontainersConfiguration.class)
class SalonSchedulingLockIT {

    private static final long HOLD_MILLIS = 500;

    @Autowired
    private SalonSchedulingLock lock;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void est_reentrant_dans_une_meme_transaction() {
        UUID salonId = UUID.randomUUID();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            lock.lock(salonId);
            lock.lock(salonId);
        });
    }

    @Test
    void serialise_deux_transactions_sur_le_meme_salon() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UUID salonId = UUID.randomUUID();
        CountDownLatch firstHoldsLock = new CountDownLatch(1);
        AtomicLong firstEnd = new AtomicLong();
        AtomicLong secondAcquired = new AtomicLong();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> tx.executeWithoutResult(status -> {
                lock.lock(salonId);
                firstHoldsLock.countDown();
                sleep(HOLD_MILLIS);
                firstEnd.set(System.nanoTime());
            }));
            Future<?> second = executor.submit(() -> {
                await(firstHoldsLock);
                tx.executeWithoutResult(status -> {
                    lock.lock(salonId);
                    secondAcquired.set(System.nanoTime());
                });
            });
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(secondAcquired.get()).isGreaterThanOrEqualTo(firstEnd.get());
    }

    @Test
    void ne_bloque_pas_un_autre_salon() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UUID salonId = UUID.randomUUID();
        UUID otherSalonId = UUID.randomUUID();
        CountDownLatch firstHoldsLock = new CountDownLatch(1);
        AtomicLong firstEnd = new AtomicLong();
        AtomicLong otherAcquired = new AtomicLong();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> tx.executeWithoutResult(status -> {
                lock.lock(salonId);
                firstHoldsLock.countDown();
                sleep(HOLD_MILLIS);
                firstEnd.set(System.nanoTime());
            }));
            Future<?> other = executor.submit(() -> {
                await(firstHoldsLock);
                tx.executeWithoutResult(status -> {
                    lock.lock(otherSalonId);
                    otherAcquired.set(System.nanoTime());
                });
            });
            first.get(20, TimeUnit.SECONDS);
            other.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(otherAcquired.get()).isLessThan(firstEnd.get());
    }
}
