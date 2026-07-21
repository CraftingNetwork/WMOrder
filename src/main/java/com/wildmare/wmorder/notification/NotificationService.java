package com.wildmare.wmorder.notification;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wildmare.wmorder.config.ConfigManager;
import com.wildmare.wmorder.config.Messages;
import com.wildmare.wmorder.database.DatabaseManager;
import com.wildmare.wmorder.database.repository.PlayerSettingsRepository;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class NotificationService {
    private final Plugin plugin;private final ConfigManager configs;private final Messages messages;private final DatabaseManager database;private final PlayerSettingsRepository settings;
    private final Cache<UUID,Boolean> enabled=Caffeine.newBuilder().maximumSize(10000).expireAfterAccess(Duration.ofMinutes(30)).build();
    public NotificationService(Plugin plugin,ConfigManager configs,Messages messages,DatabaseManager database,PlayerSettingsRepository settings){this.plugin=plugin;this.configs=configs;this.messages=messages;this.database=database;this.settings=settings;}
    public CompletableFuture<Boolean> enabled(UUID player){Boolean cached=enabled.getIfPresent(player);if(cached!=null)return CompletableFuture.completedFuture(cached);return database.supplyAsync(()->settings.notificationsEnabled(player)).thenApply(v->{enabled.put(player,v);return v;});}
    public CompletableFuture<Boolean> toggle(UUID player){return enabled(player).thenCompose(current->database.runAsync(()->settings.setNotifications(player,!current)).thenApply(v->{enabled.put(player,!current);return !current;}));}
    public void notify(UUID player,String key,Map<String,?> placeholders){enabled(player).thenAccept(allowed->{if(!allowed)return;Bukkit.getScheduler().runTask(plugin,()->{Player online=Bukkit.getPlayer(player);if(online==null)return;var n=configs.settings().notifications();var component=messages.render(key,placeholders);if(n.chat())online.sendMessage(component);if(n.actionBar())online.sendActionBar(component);if(n.title())online.showTitle(Title.title(component,net.kyori.adventure.text.Component.empty()));if(n.sound())online.playSound(online.getLocation(),Sound.ENTITY_EXPERIENCE_ORB_PICKUP,0.7f,1.2f);});});}
}
