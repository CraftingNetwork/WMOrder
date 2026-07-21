package com.wildmare.wmorder.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

public final class OrderCollectedEvent extends Event {
    private static final HandlerList HANDLERS=new HandlerList();private final Player player;private final long items;private final BigDecimal money;private final boolean partial;
    public OrderCollectedEvent(Player player,long items,BigDecimal money,boolean partial){super(false);this.player=player;this.items=items;this.money=money;this.partial=partial;}
    public Player getPlayer(){return player;}public long getItems(){return items;}public BigDecimal getMoney(){return money;}public boolean isPartial(){return partial;}
    @Override public @NotNull HandlerList getHandlers(){return HANDLERS;}public static HandlerList getHandlerList(){return HANDLERS;}
}
