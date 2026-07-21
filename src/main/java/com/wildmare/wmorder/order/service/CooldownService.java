package com.wildmare.wmorder.order.service;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CooldownService {
    private final Map<String,Long> until=new ConcurrentHashMap<>();
    private final Clock clock;
    public CooldownService(){this(Clock.systemUTC());}
    CooldownService(Clock clock){this.clock=clock;}
    public long remaining(UUID player,String action){long left=until.getOrDefault(player+":"+action,0L)-clock.millis();return Math.max(0,(left+999)/1000);}
    public void apply(UUID player,String action,int seconds){if(seconds>0)until.put(player+":"+action,clock.millis()+seconds*1000L);}
    public boolean ready(UUID player,String action){return remaining(player,action)==0;}
}
