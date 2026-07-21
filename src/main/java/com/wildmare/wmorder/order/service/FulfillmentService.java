package com.wildmare.wmorder.order.service;

import com.wildmare.wmorder.api.event.*;
import com.wildmare.wmorder.config.ConfigManager;
import com.wildmare.wmorder.database.DatabaseManager;
import com.wildmare.wmorder.database.repository.MarketplaceTransactionRepository;
import com.wildmare.wmorder.economy.*;
import com.wildmare.wmorder.item.*;
import com.wildmare.wmorder.listener.PlayerTransactionGate;
import com.wildmare.wmorder.notification.NotificationService;
import com.wildmare.wmorder.order.model.*;
import com.wildmare.wmorder.order.transaction.OrderGuard;
import com.wildmare.wmorder.permission.*;
import com.wildmare.wmorder.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FulfillmentService {
    private final JavaPlugin plugin;private final ConfigManager configs;private final DatabaseManager database;private final OrderQueryService queries;
    private final MarketplaceTransactionRepository transactions;private final EconomyService economy;private final ItemSerializer serializer;
    private final InventoryItemService inventory;private final PriceCalculator prices;private final LimitService limits;private final CooldownService cooldowns;
    private final OrderGuard guards;private final PlayerTransactionGate gate;private final NotificationService notifications;private final AtomicBoolean accepting;

    public FulfillmentService(JavaPlugin plugin,ConfigManager configs,DatabaseManager database,OrderQueryService queries,MarketplaceTransactionRepository transactions,
                              EconomyService economy,ItemSerializer serializer,InventoryItemService inventory,PriceCalculator prices,LimitService limits,
                              CooldownService cooldowns,OrderGuard guards,PlayerTransactionGate gate,NotificationService notifications,AtomicBoolean accepting){
        this.plugin=plugin;this.configs=configs;this.database=database;this.queries=queries;this.transactions=transactions;this.economy=economy;this.serializer=serializer;
        this.inventory=inventory;this.prices=prices;this.limits=limits;this.cooldowns=cooldowns;this.guards=guards;this.gate=gate;this.notifications=notifications;this.accepting=accepting;
    }

    public CompletableFuture<OperationResult<OrderReservation>> fulfill(Player seller,UUID orderId,long requested,UUID sessionId){
        if(!accepting.get())return CompletableFuture.completedFuture(OperationResult.failure("not_ready","Marketplace unavailable"));
        if(requested<=0)return CompletableFuture.completedFuture(OperationResult.failure("invalid_quantity","Quantity must be positive"));
        long wait=cooldowns.remaining(seller.getUniqueId(),"fulfill");LimitProfile profile=limits.resolve(seller);
        if(wait>0&&!seller.hasPermission("wmorder.bypass.cooldown"))return CompletableFuture.completedFuture(OperationResult.failure("cooldown",Long.toString(wait)));
        OrderGuard.Token token=guards.tryAcquire(orderId);if(token==null)return CompletableFuture.completedFuture(OperationResult.failure("busy","Order is being modified"));
        if(!gate.enter(seller.getUniqueId())){token.close();return CompletableFuture.completedFuture(OperationResult.failure("busy","Another transaction is active"));}
        CompletableFuture<OperationResult<OrderReservation>> flow=queries.find(orderId).thenCompose(optional->{
            if(optional.isEmpty())return CompletableFuture.completedFuture(OperationResult.<PreparedSale>failure("order_not_found","not found"));
            return MainThread.supply(plugin,()->prepare(seller,optional.get(),requested,profile));
        }).thenCompose(preparedResult->{
            if(!preparedResult.success())return CompletableFuture.completedFuture(OperationResult.<OrderReservation>failure(preparedResult.code(),preparedResult.detail()));
            PreparedSale prepared=preparedResult.value();UUID tx=UUID.randomUUID();String key="fulfill:"+seller.getUniqueId()+":"+orderId+":"+sessionId;
            return database.supplyAsync(()->transactions.reserveFulfillment(orderId,seller.getUniqueId(),prepared.plan().quantity(),prepared.payout(),tx,key,Instant.now()))
                    .thenCompose(reservedResult->{
                        if(!reservedResult.success())return CompletableFuture.completedFuture(reservedResult);
                        OrderReservation reservation=reservedResult.value();
                        return MainThread.supply(plugin,()->{
                            if(!seller.isOnline())return OperationResult.<InventoryItemService.RemovalReceipt>failure("offline","Seller disconnected");
                            InventorySalePlan exactPlan=inventory.plan(seller.getInventory(),prepared.template(),reservation.quantity());
                            if(exactPlan.quantity()!=reservation.quantity())return OperationResult.<InventoryItemService.RemovalReceipt>failure("inventory_changed","Matching quantity changed");
                            return inventory.apply(seller.getInventory(),prepared.template(),exactPlan);
                        }).thenCompose(removalResult->{
                            if(!removalResult.success())return database.supplyAsync(()->transactions.rollbackReservation(reservation,removalResult.detail()))
                                    .thenApply(v->OperationResult.<OrderReservation>failure(removalResult.code(),removalResult.detail()));
                            InventoryItemService.RemovalReceipt receipt=removalResult.value();
                            return database.runAsync(()->transactions.securePurchasedItems(reservation,reservation.itemBlob()))
                                    .handle((v,error)->error).thenCompose(error->{
                                        if(error!=null){
                                            return MainThread.run(plugin,()->inventory.restoreOriginalSlots(seller.getInventory(),receipt))
                                                    .thenCompose(v->database.supplyAsync(()->transactions.rollbackReservation(reservation,"item persistence failed: "+root(error).getMessage())))
                                                    .thenApply(v->OperationResult.<OrderReservation>failure("database_failure","Items restored; sale cancelled"));
                                        }
                                        return MainThread.supply(plugin,()->economy.deposit(seller,reservation.net())).thenCompose(payout->{
                                            CompletableFuture<Void> recorded=payout.success()?database.runAsync(()->transactions.completeSellerPayout(reservation,payout.response()))
                                                    :database.runAsync(()->transactions.queueSellerPayout(reservation,payout.response()));
                                            return recorded.thenCompose(v->MainThread.supply(plugin,()->{
                                                cooldowns.apply(seller.getUniqueId(),"fulfill",profile.fulfillmentCooldownSeconds());queries.invalidate();
                                                Bukkit.getPluginManager().callEvent(new OrderFulfilledEvent(seller,reservation.orderId(),reservation.quantity(),reservation.gross(),reservation.filled()));
                                                notifications.notify(reservation.buyerUuid(),reservation.filled()?"order-filled":"order-partial",Map.of("order",OrderService.shortId(reservation.orderId()),"quantity",reservation.quantity()));
                                                return OperationResult.success(reservation);
                                            }));
                                        });
                                    });
                        });
                    });
        });
        return flow.exceptionally(error->{plugin.getLogger().severe("Fulfillment failed: "+root(error).getMessage());return OperationResult.failure("transaction_failure",root(error).getMessage());})
                .whenComplete((result,error)->{gate.leave(seller.getUniqueId());token.close();});
    }

    private OperationResult<PreparedSale> prepare(Player seller,BuyOrder order,long requested,LimitProfile profile){
        if(!seller.isOnline())return OperationResult.failure("offline","Seller disconnected");
        if(!order.acceptsFulfillment(Instant.now()))return OperationResult.failure("order_inactive",order.status().name());
        if(order.buyerUuid().equals(seller.getUniqueId()))return OperationResult.failure("self_fulfillment","Cannot fulfill your own order");
        ItemStackHolder holder=deserialize(order.itemBlob());if(holder.error()!=null)return OperationResult.failure("item_error",holder.error());
        long maximum=Math.min(Math.min(requested,order.remainingQuantity()),configs.settings().orders().maximumItemsPerTransaction());
        InventorySalePlan plan=inventory.plan(seller.getInventory(),holder.item(),maximum);if(plan.quantity()<=0)return OperationResult.failure("no_items","No matching items");
        OrderFulfillEvent event=new OrderFulfillEvent(seller,order,plan.quantity());Bukkit.getPluginManager().callEvent(event);if(event.isCancelled())return OperationResult.failure("cancelled","Cancelled by another plugin");
        long quantity=Math.min(event.getQuantity(),plan.quantity());plan=inventory.plan(seller.getInventory(),holder.item(),quantity);if(plan.quantity()!=quantity)return OperationResult.failure("inventory_changed","Inventory changed");
        BigDecimal gross=order.pricePerItem().multiply(BigDecimal.valueOf(quantity));MoneyBreakdown payout=prices.sellerPayout(gross,profile);
        return OperationResult.success(new PreparedSale(order,holder.item(),plan,payout));
    }
    private ItemStackHolder deserialize(byte[] bytes){try{return new ItemStackHolder(serializer.deserialize(bytes),null);}catch(RuntimeException e){return new ItemStackHolder(null,e.getMessage());}}
    private static Throwable root(Throwable t){Throwable v=t;while(v instanceof CompletionException&&v.getCause()!=null)v=v.getCause();return v;}
    private record PreparedSale(BuyOrder order,org.bukkit.inventory.ItemStack template,InventorySalePlan plan,MoneyBreakdown payout){}
    private record ItemStackHolder(org.bukkit.inventory.ItemStack item,String error){}
}
