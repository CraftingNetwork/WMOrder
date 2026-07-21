package com.wildmare.wmorder.api.event;

import com.wildmare.wmorder.order.model.BuyOrder;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class OrderCancelEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS=new HandlerList();private final CommandSender actor;private final BuyOrder order;private boolean cancelled;
    public OrderCancelEvent(CommandSender actor,BuyOrder order){super(false);this.actor=actor;this.order=order;}
    public CommandSender getActor(){return actor;}public BuyOrder getOrder(){return order;}
    @Override public boolean isCancelled(){return cancelled;}@Override public void setCancelled(boolean cancelled){this.cancelled=cancelled;}
    @Override public @NotNull HandlerList getHandlers(){return HANDLERS;}public static HandlerList getHandlerList(){return HANDLERS;}
}
