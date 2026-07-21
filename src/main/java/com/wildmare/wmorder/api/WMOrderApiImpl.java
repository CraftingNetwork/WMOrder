package com.wildmare.wmorder.api;

import com.wildmare.wmorder.database.DatabaseManager;
import com.wildmare.wmorder.database.repository.DeliveryRepository;
import com.wildmare.wmorder.order.model.*;
import com.wildmare.wmorder.order.service.*;
import com.wildmare.wmorder.util.OperationResult;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WMOrderApiImpl implements WMOrderApi {
    private final OrderQueryService queries;private final OrderService orders;private final FulfillmentService fulfillment;private final DatabaseManager database;private final DeliveryRepository deliveries;private final AtomicBoolean ready;
    public WMOrderApiImpl(OrderQueryService queries,OrderService orders,FulfillmentService fulfillment,DatabaseManager database,DeliveryRepository deliveries,AtomicBoolean ready){this.queries=queries;this.orders=orders;this.fulfillment=fulfillment;this.database=database;this.deliveries=deliveries;this.ready=ready;}
    @Override public CompletableFuture<OrderPage> queryOrders(OrderQuery query){return queries.query(query);}
    @Override public CompletableFuture<Optional<BuyOrder>> findOrder(UUID orderId){return queries.find(orderId);}
    @Override public CompletableFuture<OperationResult<BuyOrder>> createOrder(Player player,OrderDraft draft){return orders.create(player,draft);}
    @Override public CompletableFuture<OperationResult<OrderReservation>> fulfillOrder(Player seller,UUID orderId,long quantity,UUID session){return fulfillment.fulfill(seller,orderId,quantity,session);}
    @Override public CompletableFuture<OperationResult<BigDecimal>> cancelOwnOrder(Player player,UUID orderId,String reason){return orders.cancel(player,orderId,false,reason);}
    @Override public CompletableFuture<Long> pendingCollectionCount(UUID player){return database.supplyAsync(()->deliveries.pendingCount(player));}
    @Override public boolean isReady(){return ready.get();}
}
