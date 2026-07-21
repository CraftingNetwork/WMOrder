package com.wildmare.wmorder.api;

import com.wildmare.wmorder.order.model.*;
import com.wildmare.wmorder.util.OperationResult;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public asynchronous API. Futures complete off-thread unless a method explicitly needs a Player inventory/economy operation.
 * Event callbacks are fired synchronously on the server thread after their documented transaction phase.
 */
public interface WMOrderApi {
    CompletableFuture<OrderPage> queryOrders(OrderQuery query);
    CompletableFuture<Optional<BuyOrder>> findOrder(UUID orderId);
    CompletableFuture<OperationResult<BuyOrder>> createOrder(Player player, OrderDraft draft);
    CompletableFuture<OperationResult<OrderReservation>> fulfillOrder(Player seller, UUID orderId, long quantity, UUID idempotencySession);
    CompletableFuture<OperationResult<BigDecimal>> cancelOwnOrder(Player player, UUID orderId, String reason);
    CompletableFuture<Long> pendingCollectionCount(UUID player);
    boolean isReady();
}
