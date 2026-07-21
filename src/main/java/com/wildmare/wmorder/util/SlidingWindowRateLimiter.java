package com.wildmare.wmorder.util;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SlidingWindowRateLimiter<K> {
    private final int maximum;
    private final long windowMillis;
    private final Clock clock;
    private final Map<K, ArrayDeque<Long>> windows = new ConcurrentHashMap<>();

    public SlidingWindowRateLimiter(int maximum, long windowMillis) {
        this(maximum, windowMillis, Clock.systemUTC());
    }

    SlidingWindowRateLimiter(int maximum, long windowMillis, Clock clock) {
        this.maximum = Math.max(1, maximum);
        this.windowMillis = Math.max(1, windowMillis);
        this.clock = clock;
    }

    public boolean tryAcquire(K key) {
        long now = clock.millis();
        ArrayDeque<Long> queue = windows.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && queue.peekFirst() <= now - windowMillis) queue.removeFirst();
            if (queue.size() >= maximum) return false;
            queue.addLast(now);
            return true;
        }
    }

    public void clear(K key) { windows.remove(key); }
    public void clearAll() { windows.clear(); }
}
