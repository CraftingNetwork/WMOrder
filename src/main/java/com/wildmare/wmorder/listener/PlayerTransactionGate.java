package com.wildmare.wmorder.listener;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerTransactionGate {
    private final Set<UUID> active=ConcurrentHashMap.newKeySet();
    public boolean enter(UUID player){return active.add(player);}public void leave(UUID player){active.remove(player);}public boolean active(UUID player){return active.contains(player);}public int size(){return active.size();}public void clear(){active.clear();}
}
