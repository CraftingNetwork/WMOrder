package com.wildmare.wmorder.order.service;

import com.wildmare.wmorder.api.event.*;
import com.wildmare.wmorder.config.ConfigManager;
import com.wildmare.wmorder.database.DatabaseManager;
import com.wildmare.wmorder.database.model.*;
import com.wildmare.wmorder.database.repository.*;
import com.wildmare.wmorder.economy.*;
import com.wildmare.wmorder.item.*;
import com.wildmare.wmorder.listener.PlayerTransactionGate;
import com.wildmare.wmorder.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class CollectionService {
    public record CollectionResult(long items,BigDecimal money,boolean partial,int entries){}
    private final JavaPlugin plugin;private final ConfigManager configs;private final DatabaseManager database;private final DeliveryRepository deliveries;
    private final MarketplaceTransactionRepository transactions;private final EconomyService economy;private final ItemSerializer serializer;private final InventoryItemService inventory;private final PlayerTransactionGate gate;
    public CollectionService(JavaPlugin plugin,ConfigManager configs,DatabaseManager database,DeliveryRepository deliveries,MarketplaceTransactionRepository transactions,
                             EconomyService economy,ItemSerializer serializer,InventoryItemService inventory,PlayerTransactionGate gate){this.plugin=plugin;this.configs=configs;this.database=database;this.deliveries=deliveries;this.transactions=transactions;this.economy=economy;this.serializer=serializer;this.inventory=inventory;this.gate=gate;}

    public CompletableFuture<OperationResult<CollectionResult>> collect(Player player){
        if(!gate.enter(player.getUniqueId()))return CompletableFuture.completedFuture(OperationResult.failure("busy","Another transaction is active"));
        OrderCollectEvent event=new OrderCollectEvent(player);Bukkit.getPluginManager().callEvent(event);if(event.isCancelled()){gate.leave(player.getUniqueId());return CompletableFuture.completedFuture(OperationResult.failure("cancelled","Cancelled by another plugin"));}
        UUID claim=UUID.randomUUID();int limit=configs.settings().orders().maximumPendingCollectionEntries();
        CompletableFuture<OperationResult<CollectionResult>> flow=database.supplyAsync(()->deliveries.claim(player.getUniqueId(),limit,claim)).thenCompose(entries->{
            if(entries.isEmpty())return CompletableFuture.completedFuture(OperationResult.failure("empty","Nothing to collect"));
            AtomicLong items=new AtomicLong();AtomicReference<BigDecimal> money=new AtomicReference<>(BigDecimal.ZERO);AtomicBoolean partial=new AtomicBoolean();
            CompletableFuture<Void> chain=CompletableFuture.completedFuture(null);
            for(DeliveryEntry entry:entries)chain=chain.thenCompose(v->process(player,entry,claim,items,money,partial));
            return chain.thenCompose(v->MainThread.supply(plugin,()->{CollectionResult result=new CollectionResult(items.get(),money.get(),partial.get(),entries.size());Bukkit.getPluginManager().callEvent(new OrderCollectedEvent(player,result.items(),result.money(),result.partial()));return OperationResult.success(result);}));
        });
        return flow.exceptionally(error->{plugin.getLogger().severe("Collection failed: "+root(error).getMessage());return OperationResult.failure("collection_failure",root(error).getMessage());})
                .whenComplete((r,e)->gate.leave(player.getUniqueId()));
    }

    private CompletableFuture<Void> process(Player player,DeliveryEntry entry,UUID claim,AtomicLong items,AtomicReference<BigDecimal> money,AtomicBoolean partial){
        if(entry.isItem())return MainThread.supply(plugin,()->{
            if(!player.isOnline())return 0L;ItemStack template=serializer.deserialize(entry.itemBlob());long capacity=inventory.simulatedCapacity(player.getInventory(),template,entry.quantity());
            if(capacity<=0)return 0L;return inventory.insertAsMuch(player.getInventory(),template,capacity);
        }).thenCompose(inserted->{long remaining=entry.quantity()-inserted;DeliveryStatus status=remaining==0?DeliveryStatus.DELIVERED:DeliveryStatus.READY;if(remaining>0)partial.set(true);items.addAndGet(inserted);
            return database.runAsync(()->deliveries.finalizeClaim(entry.id(),claim,remaining,BigDecimal.ZERO,status));});
        if(entry.isMoney())return MainThread.supply(plugin,()->player.isOnline()?economy.deposit(player,entry.amount()):new EconomyResult(false,entry.amount(),BigDecimal.ZERO,"player offline"))
                .thenCompose(result->{if(!result.success()){partial.set(true);return database.runAsync(()->deliveries.releaseClaim(entry.id(),claim,DeliveryStatus.READY,result.response()));}
                    money.updateAndGet(v->v.add(entry.amount()));return database.runAsync(()->transactions.completeMoneyDelivery(entry,claim,result.response()));});
        return database.runAsync(()->deliveries.finalizeClaim(entry.id(),claim,0,BigDecimal.ZERO,DeliveryStatus.DELIVERED));
    }
    private static Throwable root(Throwable t){Throwable v=t;while(v instanceof CompletionException&&v.getCause()!=null)v=v.getCause();return v;}
}
