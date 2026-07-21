package com.wildmare.wmorder.command;

import org.bukkit.command.CommandSender;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AdminConfirmationService {
    private final Map<String,Instant> pending=new ConcurrentHashMap<>();
    public boolean confirm(CommandSender sender,String action,boolean confirmationWordPresent){
        String key=senderKey(sender)+":"+action;Instant now=Instant.now();pending.entrySet().removeIf(entry->entry.getValue().isBefore(now));
        if(confirmationWordPresent){Instant expiry=pending.remove(key);return expiry!=null&&expiry.isAfter(now);}
        pending.put(key,now.plusSeconds(30));return false;
    }
    private String senderKey(CommandSender sender){return sender instanceof org.bukkit.entity.Player p?p.getUniqueId().toString():"CONSOLE";}
    public void clear(){pending.clear();}
}
