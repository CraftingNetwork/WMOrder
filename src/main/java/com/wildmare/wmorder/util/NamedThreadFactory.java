package com.wildmare.wmorder.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class NamedThreadFactory implements ThreadFactory {
    private final String prefix;
    private final AtomicInteger counter = new AtomicInteger();
    public NamedThreadFactory(String prefix) { this.prefix = prefix; }
    @Override public Thread newThread(Runnable task) {
        Thread thread = new Thread(task, prefix + "-" + counter.incrementAndGet());
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((t, e) -> e.printStackTrace());
        return thread;
    }
}
