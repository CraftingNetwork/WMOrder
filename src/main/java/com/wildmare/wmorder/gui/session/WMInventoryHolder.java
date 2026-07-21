package com.wildmare.wmorder.gui.session;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class WMInventoryHolder implements InventoryHolder {
    private final UUID sessionId;private Inventory inventory;
    public WMInventoryHolder(UUID sessionId){this.sessionId=sessionId;}public UUID sessionId(){return sessionId;}public void inventory(Inventory inventory){this.inventory=inventory;}
    @Override public @NotNull Inventory getInventory(){if(inventory==null)throw new IllegalStateException("Inventory not attached");return inventory;}
}
