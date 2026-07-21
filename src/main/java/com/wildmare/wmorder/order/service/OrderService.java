package com.wildmare.wmorder.order.service;

import com.wildmare.wmorder.api.event.*;
import com.wildmare.wmorder.config.ConfigManager;
import com.wildmare.wmorder.database.DatabaseManager;
import com.wildmare.wmorder.database.model.*;
import com.wildmare.wmorder.database.repository.*;
import com.wildmare.wmorder.economy.*;
import com.wildmare.wmorder.item.*;
import com.wildmare.wmorder.notification.NotificationService;
import com.wildmare.wmorder.order.model.*;
import com.wildmare.wmorder.order.validation.OrderValidationService;
import com.wildmare.wmorder.permission.*;
import com.wildmare.wmorder.util.*;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class OrderService {
    private final JavaPlugin plugin;private final ConfigManager configs;private final DatabaseManager database;private final OrderQueryService queries;
    private final LedgerRepository ledger;private final MarketplaceTransactionRepository transactions;private final EconomyService economy;
    private final ItemMatcher matcher;private final OrderValidationService validation;private final PriceCalculator prices;private final LimitService limits;
    private final CooldownService cooldowns;private final NotificationService notifications;private final MoneyMath money;private final AtomicBoolean accepting;
    private final java.util.Set<UUID> creating=ConcurrentHashMap.newKeySet();

    public OrderService(JavaPlugin plugin,ConfigManager configs,DatabaseManager database,OrderQueryService queries,LedgerRepository ledger,
                        MarketplaceTransactionRepository transactions,EconomyService economy,ItemMatcher matcher,
                        OrderValidationService validation,PriceCalculator prices,LimitService limits,CooldownService cooldowns,
                        NotificationService notifications,MoneyMath money,AtomicBoolean accepting){
        this.plugin=plugin;this.configs=configs;this.database=database;this.queries=queries;this.ledger=ledger;this.transactions=transactions;this.economy=economy;
        this.matcher=matcher;this.validation=validation;this.prices=prices;this.limits=limits;this.cooldowns=cooldowns;this.notifications=notifications;this.money=money;this.accepting=accepting;
    }

    public CompletableFuture<OperationResult<BuyOrder>> create(Player player,OrderDraft draft){
        if(!accepting.get())return CompletableFuture.completedFuture(OperationResult.failure("not_ready","Marketplace is not accepting transactions"));
        if(!creating.add(player.getUniqueId()))return CompletableFuture.completedFuture(OperationResult.failure("duplicate","A creation transaction is already active"));
        LimitProfile profile=limits.resolve(player);
        OperationResult<BigDecimal> checked=validation.validate(draft,profile);
        if(!checked.success()){creating.remove(player.getUniqueId());return CompletableFuture.completedFuture(OperationResult.failure(checked.code(),checked.detail()));}
        long wait=cooldowns.remaining(player.getUniqueId(),"create");
        if(wait>0&&!player.hasPermission("wmorder.bypass.cooldown")){creating.remove(player.getUniqueId());return CompletableFuture.completedFuture(OperationResult.failure("cooldown",Long.toString(wait)));}
        BigDecimal total=checked.value();
        CompletableFuture<OperationResult<BuyOrder>> future=queries.countActive(player.getUniqueId()).thenCompose(active->{
            if(active>=profile.maxActiveOrders()&&!player.hasPermission("wmorder.bypass.limit"))return CompletableFuture.completedFuture(OperationResult.<CreationContext>failure("active_limit","Maximum active orders reached"));
            return MainThread.supply(plugin,()->prepareCreation(player,draft,profile,total));
        }).thenCompose(preparedResult->{
            if(!preparedResult.success())return CompletableFuture.completedFuture(OperationResult.<BuyOrder>failure(preparedResult.code(),preparedResult.detail()));
            CreationContext context=preparedResult.value();
            return database.supplyAsync(()->ledger.insertPrepared(context.ledger())).thenCompose(inserted->{
                if(!inserted)return CompletableFuture.completedFuture(OperationResult.failure("duplicate","Duplicate idempotency key"));
                return MainThread.supply(plugin,()->economy.withdraw(player,context.breakdown().netOrDeposit())).thenCompose(withdraw->{
                    if(!withdraw.success())return database.runAsync(()->ledger.updateState(context.ledger().id(),TransactionState.FAILED,withdraw.response(),"withdraw-failed"))
                            .thenApply(v->OperationResult.<BuyOrder>failure("insufficient_funds",withdraw.response()));
                    return database.runAsync(()->ledger.updateState(context.ledger().id(),TransactionState.ECONOMY_APPLIED,withdraw.response(),"withdraw-complete"))
                            .thenCompose(v->database.runAsync(()->transactions.commitCreatedOrder(context.order(),context.ledger().id(),withdraw.response())))
                            .thenCompose(v->MainThread.supply(plugin,()->{
                                cooldowns.apply(player.getUniqueId(),"create",profile.creationCooldownSeconds());queries.invalidate();
                                Bukkit.getPluginManager().callEvent(new OrderCreatedEvent(player,context.order()));
                                notifications.notify(player.getUniqueId(),"order-created",Map.of("order",shortId(context.order().id()),"quantity",context.order().requestedQuantity(),"item",context.order().itemDisplayName(),"price",context.order().pricePerItem()));
                                return OperationResult.success(context.order());
                            })).exceptionallyCompose(error->compensateCreation(player,context,withdraw,error));
                });
            });
        });
        return future.whenComplete((result,error)->creating.remove(player.getUniqueId()));
    }

    private OperationResult<CreationContext> prepareCreation(Player player,OrderDraft draft,LimitProfile profile,BigDecimal total){
        if(!player.isOnline())return OperationResult.failure("offline","Player disconnected");
        OrderCreateEvent event=new OrderCreateEvent(player,draft);Bukkit.getPluginManager().callEvent(event);if(event.isCancelled())return OperationResult.failure("cancelled","Cancelled by another plugin");
        ItemIdentity identity=matcher.identity(draft.item());BigDecimal normalizedPrice=money.normalize(draft.pricePerItem());MoneyBreakdown breakdown=prices.creation(total,profile);BigDecimal balance=economy.balance(player);
        if(balance.compareTo(breakdown.netOrDeposit())<0)return OperationResult.failure("insufficient_funds",balance.toPlainString());
        Instant now=Instant.now();UUID orderId=UUID.randomUUID();UUID ledgerId=UUID.randomUUID();
        BuyOrder order=new BuyOrder(orderId,player.getUniqueId(),player.getName(),identity.fingerprint(),identity.material(),identity.displayName(),identity.serialized(),
                draft.quantity(),draft.quantity(),0,normalizedPrice,total,total,now,now.plus(draft.duration()),now,OrderStatus.ACTIVE,draft.category(),configs.settings().serverId(),0,draft.idempotencyKey());
        LedgerEntry entry=new LedgerEntry(ledgerId,draft.idempotencyKey(),orderId,player.getUniqueId(),TransactionType.ORDER_DEPOSIT,total,breakdown.totalFee(),breakdown.netOrDeposit(),null,TransactionState.PREPARED,"order="+orderId,now,now);
        return OperationResult.success(new CreationContext(order,entry,breakdown));
    }

    private CompletableFuture<OperationResult<BuyOrder>> compensateCreation(Player player,CreationContext context,EconomyResult withdrawal,Throwable error){
        plugin.getLogger().severe("Order creation commit failed for "+player.getUniqueId()+": "+root(error).getMessage());
        return MainThread.supply(plugin,()->economy.deposit(player,context.breakdown().netOrDeposit())).thenCompose(refund->{
            if(refund.success())return database.runAsync(()->ledger.updateState(context.ledger().id(),TransactionState.COMPENSATED,refund.response(),"creation-failed-refunded"))
                    .thenApply(v->OperationResult.failure("database_failure","Order creation failed and was refunded"));
            return database.runAsync(()->transactions.queueCreationRefund(context.ledger().id(),player.getUniqueId(),context.breakdown().netOrDeposit(),refund.response(),"Creation failed after withdrawal"))
                    .thenApply(v->OperationResult.failure("recovery_pending","Refund queued for collection/recovery"));
        });
    }

    public CompletableFuture<OperationResult<BigDecimal>> cancel(CommandSender actor,UUID orderId,boolean admin,String reason){
        if(!accepting.get())return CompletableFuture.completedFuture(OperationResult.failure("not_ready","Marketplace unavailable"));
        return queries.find(orderId).thenCompose(optional->{
            if(optional.isEmpty())return CompletableFuture.completedFuture(OperationResult.failure("order_not_found","not found"));BuyOrder order=optional.get();
            return MainThread.supply(plugin,()->{OrderCancelEvent event=new OrderCancelEvent(actor,order);Bukkit.getPluginManager().callEvent(event);return event.isCancelled();}).thenCompose(cancelled->{
                if(cancelled)return CompletableFuture.completedFuture(OperationResult.failure("cancelled","Cancelled by another plugin"));
                LimitProfile profile=limits.resolve(actor);MoneyBreakdown refund=prices.cancellationRefund(order.remainingReservedBalance(),profile);
                UUID actorId=actor instanceof Player p?p.getUniqueId():null;
                return database.supplyAsync(()->transactions.cancel(orderId,actorId,admin,refund,reason)).thenCompose(result->MainThread.supply(plugin,()->{
                    if(result.success()){queries.invalidate();Bukkit.getPluginManager().callEvent(new OrderCancelledEvent(actor,orderId,result.value()));notifications.notify(order.buyerUuid(),"order-cancelled",Map.of("order",shortId(orderId)));}
                    return result;
                }));
            });
        });
    }

    private static Throwable root(Throwable throwable){Throwable value=throwable;while(value instanceof CompletionException&&value.getCause()!=null)value=value.getCause();return value;}
    public static String shortId(UUID id){return id.toString().substring(0,8);}
    private record CreationContext(BuyOrder order,LedgerEntry ledger,MoneyBreakdown breakdown){}
}
