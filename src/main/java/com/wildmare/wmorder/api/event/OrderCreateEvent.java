package com.wildmare.wmorder.api.event;

import com.wildmare.wmorder.order.model.OrderDraft;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class OrderCreateEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS=new HandlerList();
    private final Player player;private final OrderDraft draft;private boolean cancelled;
    public OrderCreateEvent(Player player,OrderDraft draft){super(false);this.player=player;this.draft=draft;}
    public Player getPlayer(){return player;}public OrderDraft getDraft(){return draft;}
    @Override public boolean isCancelled(){return cancelled;}@Override public void setCancelled(boolean cancelled){this.cancelled=cancelled;}
    @Override public @NotNull HandlerList getHandlers(){return HANDLERS;}public static HandlerList getHandlerList(){return HANDLERS;}
}
