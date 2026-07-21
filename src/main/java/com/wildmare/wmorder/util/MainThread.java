package com.wildmare.wmorder.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class MainThread {
    private MainThread() {}

    public static CompletableFuture<Void> run(Plugin plugin, Runnable runnable) {
        return supply(plugin, () -> { runnable.run(); return null; });
    }

    public static <T> CompletableFuture<T> supply(Plugin plugin, Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Runnable wrapped = () -> {
            try { future.complete(supplier.get()); }
            catch (Throwable throwable) { future.completeExceptionally(throwable); }
        };
        if (Bukkit.isPrimaryThread()) wrapped.run();
        else Bukkit.getScheduler().runTask(plugin, wrapped);
        return future;
    }
}
