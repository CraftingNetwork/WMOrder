package com.wildmare.wmorder.scheduler;

import com.wildmare.wmorder.api.event.*;
import com.wildmare.wmorder.config.ConfigManager;
import com.wildmare.wmorder.database.DatabaseManager;
import com.wildmare.wmorder.database.repository.*;
import com.wildmare.wmorder.notification.NotificationService;
import com.wildmare.wmorder.order.model.BuyOrder;
import com.wildmare.wmorder.order.service.*;
import com.wildmare.wmorder.order.transaction.OrderGuard;
import com.wildmare.wmorder.util.MainThread;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ExpirationService {
    private final JavaPlugin plugin;private final ConfigManager configs;private final DatabaseManager database;private final OrderRepository orders;
    private final MarketplaceTransactionRepository transactions;private final OrderQueryService queries;private final OrderGuard guards;private final NotificationService notifications;private final AtomicBoolean running=new AtomicBoolean();
    private BukkitTask task;
    public ExpirationService(JavaPlugin plugin,ConfigManager configs,DatabaseManager database,OrderRepository orders,MarketplaceTransactionRepository transactions,OrderQueryService queries,OrderGuard guards,NotificationService notifications){this.plugin=plugin;this.configs=configs;this.database=database;this.orders=orders;this.transactions=transactions;this.queries=queries;this.guards=guards;this.notifications=notifications;}
    public void start(){long period=configs.settings().orders().expirationCheckIntervalSeconds()*20L;task=Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,this::scan,period,period);}
    public void stop(){if(task!=null)task.cancel();}
    public void scan(){if(!running.compareAndSet(false,true))return;Instant now=Instant.now();database.supplyAsync(()->orders.dueExpirations(now,configs.settings().orders().expirationBatchSize())).thenAccept(list->{for(BuyOrder order:list)process(order,now);}).whenComplete((v,e)->running.set(false));}
    private void process(BuyOrder order,Instant now){OrderGuard.Token token=guards.tryAcquire(order.id());if(token==null)return;MainThread.supply(plugin,()->{OrderExpireEvent event=new OrderExpireEvent(order);Bukkit.getPluginManager().callEvent(event);return event.isCancelled();}).thenCompose(cancelled->{if(cancelled)return java.util.concurrent.CompletableFuture.completedFuture(null);return database.supplyAsync(()->transactions.expire(order.id(),order.version(),now)).thenCompose(result->{if(!result.success())return java.util.concurrent.CompletableFuture.completedFuture(null);queries.invalidate();notifications.notify(order.buyerUuid(),"order-expired",Map.of("order",OrderService.shortId(order.id())));return MainThread.run(plugin,()->Bukkit.getPluginManager().callEvent(new OrderExpiredEvent(order.id(),order.buyerUuid(),result.value())));});}).whenComplete((v,e)->token.close());}
}
