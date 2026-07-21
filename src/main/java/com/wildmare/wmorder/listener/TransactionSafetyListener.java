package com.wildmare.wmorder.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

public final class TransactionSafetyListener implements Listener {
    private final PlayerTransactionGate gate;
    public TransactionSafetyListener(PlayerTransactionGate gate){this.gate=gate;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void click(InventoryClickEvent event){if(gate.active(event.getWhoClicked().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void drag(InventoryDragEvent event){if(gate.active(event.getWhoClicked().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void drop(PlayerDropItemEvent event){if(gate.active(event.getPlayer().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void swap(PlayerSwapHandItemsEvent event){if(gate.active(event.getPlayer().getUniqueId()))event.setCancelled(true);}
}
