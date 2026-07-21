package com.wildmare.wmorder.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class OrderCollectEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS=new HandlerList();private final Player player;private boolean cancelled;
    public OrderCollectEvent(Player player){super(false);this.player=player;}public Player getPlayer(){return player;}
    @Override public boolean isCancelled(){return cancelled;}@Override public void setCancelled(boolean cancelled){this.cancelled=cancelled;}
    @Override public @NotNull HandlerList getHandlers(){return HANDLERS;}public static HandlerList getHandlerList(){return HANDLERS;}
}
