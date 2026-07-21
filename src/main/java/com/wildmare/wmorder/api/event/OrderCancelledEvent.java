package com.wildmare.wmorder.api.event;

import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public final class OrderCancelledEvent extends Event {
    private static final HandlerList HANDLERS=new HandlerList();private final CommandSender actor;private final UUID orderId;private final BigDecimal refund;
    public OrderCancelledEvent(CommandSender actor,UUID orderId,BigDecimal refund){super(false);this.actor=actor;this.orderId=orderId;this.refund=refund;}
    public CommandSender getActor(){return actor;}public UUID getOrderId(){return orderId;}public BigDecimal getRefund(){return refund;}
    @Override public @NotNull HandlerList getHandlers(){return HANDLERS;}public static HandlerList getHandlerList(){return HANDLERS;}
}
