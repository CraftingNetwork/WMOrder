package com.wildmare.wmorder.order.model;

import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

public record OrderDraft(ItemStack item, long quantity, BigDecimal pricePerItem, Duration duration, String category,
                         String idempotencyKey) {
    public OrderDraft {
        Objects.requireNonNull(item, "item");
        item = item.clone();
        Objects.requireNonNull(pricePerItem, "pricePerItem");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    }

    @Override
    public ItemStack item() {
        return item.clone();
    }
}
