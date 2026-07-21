package com.wildmare.wmorder.simulation;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;

class TransactionSimulatorTest {
    @Test void partialThenFullFulfillmentPreservesValue() {
        var sim = new TransactionSimulator(10, new BigDecimal("2.50"));
        var first = sim.fulfill(4, new BigDecimal("2.50"), "sale-a", 0);
        var second = sim.fulfill(10, new BigDecimal("2.50"), "sale-b", 1);
        assertThat(first.sold()).isEqualTo(4);
        assertThat(second.sold()).isEqualTo(6);
        assertThat(sim.remaining()).isZero();
        assertThat(sim.fulfilled()).isEqualTo(10);
        assertThat(sim.reserved()).isZero();
    }

    @Test void duplicateIdempotencyKeyCannotPayTwice() {
        var sim = new TransactionSimulator(10, BigDecimal.ONE);
        assertThat(sim.fulfill(3, BigDecimal.ONE, "same", 0).success()).isTrue();
        assertThat(sim.fulfill(3, BigDecimal.ONE, "same", 1).success()).isFalse();
        assertThat(sim.fulfilled()).isEqualTo(3);
    }

    @Test void optimisticLockRejectsStaleVersion() {
        var sim = new TransactionSimulator(5, BigDecimal.ONE);
        assertThat(sim.fulfill(1, BigDecimal.ONE, "a", 0).success()).isTrue();
        assertThat(sim.fulfill(1, BigDecimal.ONE, "b", 0).success()).isFalse();
    }

    @Test void manyConcurrentSellersCannotOversell() throws Exception {
        var sim = new TransactionSimulator(100, BigDecimal.ONE);
        ExecutorService pool = Executors.newFixedThreadPool(16);
        AtomicLong version = new AtomicLong();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            int id = i;
            futures.add(pool.submit(() -> {
                long v = version.get();
                var result = sim.fulfill(1, BigDecimal.ONE, "sale-" + id, v);
                if (result.success()) version.compareAndSet(v, result.newVersion());
            }));
        }
        for (Future<?> f : futures) f.get(5, TimeUnit.SECONDS);
        pool.shutdownNow();
        assertThat(sim.fulfilled()).isBetween(1L, 100L);
        assertThat(sim.remaining()).isGreaterThanOrEqualTo(0);
        assertThat(sim.fulfilled() + sim.remaining()).isEqualTo(100);
        assertThat(sim.reserved()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    @Test void cancellationRefundsOnlyUnusedReserve() {
        var sim = new TransactionSimulator(10, new BigDecimal("5.00"));
        sim.fulfill(4, new BigDecimal("5.00"), "sale", 0);
        assertThat(sim.cancelRefund()).isEqualByComparingTo("30.00");
        assertThat(sim.reserved()).isZero();
    }
}
