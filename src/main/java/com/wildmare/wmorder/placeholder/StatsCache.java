package com.wildmare.wmorder.placeholder;

import com.github.benmanes.caffeine.cache.*;
import com.wildmare.wmorder.database.DatabaseManager;
import com.wildmare.wmorder.database.model.PlayerStatistics;
import com.wildmare.wmorder.database.repository.StatsRepository;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StatsCache {
    private final Plugin plugin;private final DatabaseManager database;private final StatsRepository repository;private final Cache<UUID,PlayerStatistics> cache=Caffeine.newBuilder().maximumSize(10000).expireAfterAccess(Duration.ofMinutes(15)).build();
    private final Set<UUID> tracked=ConcurrentHashMap.newKeySet();private BukkitTask task;
    public StatsCache(Plugin plugin,DatabaseManager database,StatsRepository repository){this.plugin=plugin;this.database=database;this.repository=repository;}
    public void start(){task=Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,this::refreshTracked,1200L,1200L);}public void stop(){if(task!=null)task.cancel();}
    public PlayerStatistics get(UUID player){tracked.add(player);PlayerStatistics stats=cache.getIfPresent(player);if(stats==null){cache.put(player,PlayerStatistics.empty());refresh(player);return PlayerStatistics.empty();}return stats;}
    public void refresh(UUID player){database.supplyAsync(()->repository.player(player)).thenAccept(stats->cache.put(player,stats));}
    private void refreshTracked(){for(UUID player:Set.copyOf(tracked))refresh(player);tracked.clear();}
    public void invalidate(UUID player){cache.invalidate(player);refresh(player);}
}
