package com.wildmare.wmorder.gui;

import com.wildmare.wmorder.gui.session.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.Plugin;

public final class MenuListener implements Listener {
    private final Plugin plugin;private final GuiSessionRegistry sessions;private final MenuManager menus;
    public MenuListener(Plugin plugin,GuiSessionRegistry sessions,MenuManager menus){this.plugin=plugin;this.sessions=sessions;this.menus=menus;}
    @EventHandler(priority=EventPriority.HIGHEST)public void click(InventoryClickEvent event){
        if(!(event.getView().getTopInventory().getHolder() instanceof WMInventoryHolder holder))return;event.setCancelled(true);if(!(event.getWhoClicked() instanceof Player player))return;
        MenuSession session=sessions.get(holder.sessionId()).orElse(null);if(session==null||!session.owner().equals(player.getUniqueId())||session.stale()){player.closeInventory();return;}
        if(event.getRawSlot()<0||event.getRawSlot()>=event.getView().getTopInventory().getSize())return;GuiAction action=session.action(event.getRawSlot());if(action!=null)menus.handle(player,session,action);
    }
    @EventHandler(priority=EventPriority.HIGHEST)public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof WMInventoryHolder)event.setCancelled(true);}
    @EventHandler public void close(InventoryCloseEvent event){if(!(event.getInventory().getHolder() instanceof WMInventoryHolder holder))return;MenuSession session=sessions.get(holder.sessionId()).orElse(null);if(session==null)return;if(session.busy()&&event.getPlayer() instanceof Player player){Bukkit.getScheduler().runTask(plugin,()->{if(player.isOnline()&&session.busy()&&sessions.get(session.id()).isPresent())player.openInventory(session.inventory());});}else sessions.remove(session.id());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void swap(PlayerSwapHandItemsEvent event){if(event.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof WMInventoryHolder)event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void drop(PlayerDropItemEvent event){if(event.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof WMInventoryHolder)event.setCancelled(true);}
    @EventHandler public void quit(PlayerQuitEvent event){sessions.removePlayer(event.getPlayer().getUniqueId());}
}
