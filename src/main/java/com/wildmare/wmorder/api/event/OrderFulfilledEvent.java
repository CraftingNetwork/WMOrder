package com.wildmare.wmorder.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public final class OrderFulfilledEvent extends Event {
    private static final HandlerList HANDLERS=new HandlerList();private final Player seller;private final UUID orderId;private final long quantity;private final BigDecimal gross;private final boolean filled;
    public OrderFulfilledEvent(Player seller,UUID orderId,long quantity,BigDecimal gross,boolean filled){super(false);this.seller=seller;this.orderId=orderId;this.quantity=quantity;this.gross=gross;this.filled=filled;}
    public Player getSeller(){return seller;}public UUID getOrderId(){return orderId;}public long getQuantity(){return quantity;}public BigDecimal getGross(){return gross;}public boolean isFilled(){return filled;}
    @Override public @NotNull HandlerList getHandlers(){return HANDLERS;}public static HandlerList getHandlerList(){return HANDLERS;}
}
