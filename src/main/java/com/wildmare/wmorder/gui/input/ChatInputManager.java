package com.wildmare.wmorder.gui.input;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ChatInputManager implements Listener {
    private final Plugin plugin;private final Map<UUID,Consumer<String>> inputs=new ConcurrentHashMap<>();
    public ChatInputManager(Plugin plugin){this.plugin=plugin;}
    public void request(Player player,Consumer<String> callback){inputs.put(player.getUniqueId(),callback);player.closeInventory();}
    public boolean active(UUID player){return inputs.containsKey(player);}
    public void cancel(UUID player){inputs.remove(player);}
    public void clear(){inputs.clear();}
    @EventHandler(priority=EventPriority.HIGHEST)public void chat(AsyncChatEvent event){Consumer<String> callback=inputs.remove(event.getPlayer().getUniqueId());if(callback==null)return;event.setCancelled(true);String text=PlainTextComponentSerializer.plainText().serialize(event.message()).trim();Bukkit.getScheduler().runTask(plugin,()->callback.accept(text));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void open(InventoryOpenEvent event){if(inputs.containsKey(event.getPlayer().getUniqueId()))event.setCancelled(true);}
    @EventHandler public void quit(PlayerQuitEvent event){inputs.remove(event.getPlayer().getUniqueId());}
}
