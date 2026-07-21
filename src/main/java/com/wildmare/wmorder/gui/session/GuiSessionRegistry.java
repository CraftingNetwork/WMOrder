package com.wildmare.wmorder.gui.session;

import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class GuiSessionRegistry {
    private final Map<UUID,MenuSession> sessions=new ConcurrentHashMap<>();private final Map<UUID,UUID> byPlayer=new ConcurrentHashMap<>();
    public MenuSession create(Player player,MenuType type){removePlayer(player.getUniqueId());MenuSession session=new MenuSession(UUID.randomUUID(),player.getUniqueId(),type);sessions.put(session.id(),session);byPlayer.put(player.getUniqueId(),session.id());return session;}
    public Optional<MenuSession> get(UUID id){MenuSession s=sessions.get(id);if(s!=null&&s.stale()){remove(id);return Optional.empty();}return Optional.ofNullable(s);}
    public Optional<MenuSession> player(UUID player){UUID id=byPlayer.get(player);return id==null?Optional.empty():get(id);}
    public void remove(UUID id){MenuSession removed=sessions.remove(id);if(removed!=null)byPlayer.remove(removed.owner(),id);}
    public void removePlayer(UUID player){UUID id=byPlayer.remove(player);if(id!=null)sessions.remove(id);}
    public void clear(){sessions.clear();byPlayer.clear();}
}
