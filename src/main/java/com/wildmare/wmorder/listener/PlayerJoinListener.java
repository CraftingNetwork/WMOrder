package com.wildmare.wmorder.listener;

import com.wildmare.wmorder.database.DatabaseManager;
import com.wildmare.wmorder.database.repository.DeliveryRepository;
import com.wildmare.wmorder.notification.NotificationService;
import com.wildmare.wmorder.placeholder.StatsCache;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Map;

public final class PlayerJoinListener implements Listener {
    private final DatabaseManager database;private final DeliveryRepository deliveries;private final NotificationService notifications;private final StatsCache stats;
    public PlayerJoinListener(DatabaseManager database,DeliveryRepository deliveries,NotificationService notifications,StatsCache stats){this.database=database;this.deliveries=deliveries;this.notifications=notifications;this.stats=stats;}
    @EventHandler public void join(PlayerJoinEvent event){var id=event.getPlayer().getUniqueId();stats.refresh(id);database.supplyAsync(()->deliveries.pendingCount(id)).thenAccept(count->{if(count>0)notifications.notify(id,"collection-available",Map.of("count",count));});}
}
