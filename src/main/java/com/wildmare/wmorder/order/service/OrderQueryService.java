package com.wildmare.wmorder.order.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wildmare.wmorder.config.ConfigManager;
import com.wildmare.wmorder.database.DatabaseManager;
import com.wildmare.wmorder.database.model.DeliveryEntry;
import com.wildmare.wmorder.database.repository.*;
import com.wildmare.wmorder.order.model.*;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class OrderQueryService {
    private final DatabaseManager database;private final OrderRepository orders;private final DeliveryRepository deliveries;private final HistoryRepository history;
    private final Cache<OrderQuery,OrderPage> pageCache;
    public OrderQueryService(DatabaseManager database,OrderRepository orders,DeliveryRepository deliveries,HistoryRepository history,ConfigManager configs){
        this.database=database;this.orders=orders;this.deliveries=deliveries;this.history=history;
        this.pageCache=Caffeine.newBuilder().maximumSize(Math.max(100,configs.settings().performance().summaryCacheMaximum()))
                .expireAfterWrite(Duration.ofSeconds(Math.max(1,configs.settings().performance().searchCacheSeconds()))).build();
    }
    public CompletableFuture<OrderPage> query(OrderQuery query){OrderPage cached=pageCache.getIfPresent(query);if(cached!=null)return CompletableFuture.completedFuture(cached);return database.supplyAsync(()->orders.query(query)).thenApply(page->{pageCache.put(query,page);return page;});}
    public CompletableFuture<Optional<BuyOrder>> find(UUID id){return database.supplyAsync(()->orders.find(id));}
    public CompletableFuture<Optional<UUID>> resolveId(String input){return database.supplyAsync(()->orders.resolveId(input));}
    public CompletableFuture<Long> countActive(UUID buyer){return database.supplyAsync(()->orders.countActive(buyer));}
    public CompletableFuture<List<DeliveryEntry>> readyDeliveries(UUID player,int limit){return database.supplyAsync(()->deliveries.ready(player,limit));}
    public CompletableFuture<List<HistoryRepository.HistoryLine>> history(UUID player,int page,int size){return database.supplyAsync(()->history.playerHistory(player,page,size));}
    public void invalidate(){pageCache.invalidateAll();}
    public long cacheSize(){return pageCache.estimatedSize();}
}
