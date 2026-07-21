package com.wildmare.wmorder.api.event;

import com.wildmare.wmorder.order.model.BuyOrder;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class OrderCreatedEvent extends Event {
    private static final HandlerList HANDLERS=new HandlerList();private final Player player;private final BuyOrder order;
    public OrderCreatedEvent(Player player,BuyOrder order){super(false);this.player=player;this.order=order;}
    public Player getPlayer(){return player;}public BuyOrder getOrder(){return order;}
    @Override public @NotNull HandlerList getHandlers(){return HANDLERS;}public static HandlerList getHandlerList(){return HANDLERS;}
}
