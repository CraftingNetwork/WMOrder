package com.wildmare.wmorder.order.transaction;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class OrderGuardConcurrencyTest {
    @Test void onlyOneSellerCanHoldAnOrderGuardAtOnce() throws Exception {
        OrderGuard guard = new OrderGuard();
        UUID order = UUID.randomUUID();
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> {
                try (OrderGuard.Token token = guard.tryAcquire(order)) {
                    assertThat(token).isNotNull();
                    successes.incrementAndGet();
                    acquired.countDown();
                    release.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            assertThat(acquired.await(2, TimeUnit.SECONDS)).isTrue();
            Future<?> second = pool.submit(() -> {
                try (OrderGuard.Token token = guard.tryAcquire(order)) {
                    if (token != null) successes.incrementAndGet();
                }
            });
            second.get(2, TimeUnit.SECONDS);
            assertThat(successes).hasValue(1);
            release.countDown();
            first.get(2, TimeUnit.SECONDS);
            try (OrderGuard.Token token = guard.tryAcquire(order)) { assertThat(token).isNotNull(); }
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
