package com.wildmare.wmorder.simulation;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/** Deterministic in-memory model used to stress the invariants of the SQL transaction design. */
final class TransactionSimulator {
    private final ReentrantLock lock = new ReentrantLock();
    private final Set<String> idempotency = new HashSet<>();
    private long remaining;
    private long fulfilled;
    private BigDecimal reserved;
    private long version;

    TransactionSimulator(long quantity, BigDecimal price) {
        this.remaining = quantity;
        this.reserved = price.multiply(BigDecimal.valueOf(quantity));
    }

    Result fulfill(long requested, BigDecimal price, String key, long expectedVersion) {
        lock.lock();
        try {
            if (!idempotency.add(key)) return new Result(false, 0, BigDecimal.ZERO, version);
            if (expectedVersion != version || requested <= 0 || remaining <= 0) return new Result(false, 0, BigDecimal.ZERO, version);
            long sold = Math.min(requested, remaining);
            BigDecimal payout = price.multiply(BigDecimal.valueOf(sold));
            remaining -= sold;
            fulfilled += sold;
            reserved = reserved.subtract(payout);
            version++;
            return new Result(true, sold, payout, version);
        } finally { lock.unlock(); }
    }

    BigDecimal cancelRefund() {
        lock.lock();
        try { BigDecimal value = reserved; reserved = BigDecimal.ZERO; remaining = 0; version++; return value; }
        finally { lock.unlock(); }
    }

    long remaining() { return remaining; }
    long fulfilled() { return fulfilled; }
    BigDecimal reserved() { return reserved; }
    long version() { return version; }
    record Result(boolean success, long sold, BigDecimal payout, long newVersion) {}
}
