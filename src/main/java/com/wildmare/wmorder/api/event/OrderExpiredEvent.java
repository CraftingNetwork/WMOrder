package com.wildmare.wmorder.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public final class OrderExpiredEvent extends Event {
    private static final HandlerList HANDLERS=new HandlerList();private final UUID orderId;private final UUID buyer;private final BigDecimal refund;
    public OrderExpiredEvent(UUID orderId,UUID buyer,BigDecimal refund){super(false);this.orderId=orderId;this.buyer=buyer;this.refund=refund;}
    public UUID getOrderId(){return orderId;}public UUID getBuyer(){return buyer;}public BigDecimal getRefund(){return refund;}
    @Override public @NotNull HandlerList getHandlers(){return HANDLERS;}public static HandlerList getHandlerList(){return HANDLERS;}
}
