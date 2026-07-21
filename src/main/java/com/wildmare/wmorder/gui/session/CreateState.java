package com.wildmare.wmorder.gui.session;

import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

public final class CreateState {
    private final ItemStack item;private long quantity;private BigDecimal price;private final Duration duration;private final String category;private final UUID idempotencySession;
    public CreateState(ItemStack item,long quantity,BigDecimal price,Duration duration,String category,UUID session){this.item=item.clone();this.quantity=quantity;this.price=price;this.duration=duration;this.category=category;this.idempotencySession=session;}
    public ItemStack item(){return item.clone();}public long quantity(){return quantity;}public void quantity(long quantity){this.quantity=quantity;}public BigDecimal price(){return price;}public void price(BigDecimal price){this.price=price;}public Duration duration(){return duration;}public String category(){return category;}public UUID idempotencySession(){return idempotencySession;}
}
