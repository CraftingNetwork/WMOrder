package com.wildmare.wmorder.api.event;

import com.wildmare.wmorder.order.model.BuyOrder;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class OrderFulfillEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS=new HandlerList();private final Player seller;private final BuyOrder order;private long quantity;private boolean cancelled;
    public OrderFulfillEvent(Player seller,BuyOrder order,long quantity){super(false);this.seller=seller;this.order=order;this.quantity=quantity;}
    public Player getSeller(){return seller;}public BuyOrder getOrder(){return order;}public long getQuantity(){return quantity;}
    public void setQuantity(long quantity){if(quantity<=0)throw new IllegalArgumentException("quantity must be positive");this.quantity=quantity;}
    @Override public boolean isCancelled(){return cancelled;}@Override public void setCancelled(boolean cancelled){this.cancelled=cancelled;}
    @Override public @NotNull HandlerList getHandlers(){return HANDLERS;}public static HandlerList getHandlerList(){return HANDLERS;}
}
