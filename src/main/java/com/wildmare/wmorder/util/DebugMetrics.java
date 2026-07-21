package com.wildmare.wmorder.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.*;

public final class DebugMetrics {
    private final AtomicBoolean enabled=new AtomicBoolean();private final Map<String,Metric> metrics=new ConcurrentHashMap<>();
    public boolean toggle(){
        boolean current;
        boolean next;
        do {
            current=enabled.get();
            next=!current;
        } while(!enabled.compareAndSet(current,next));
        return next;
    }
    public boolean enabled(){return enabled.get();}
    public Timer timer(String name){return enabled()?new Timer(name,System.nanoTime()):new Timer(null,0);}
    public Map<String,String> snapshot(){Map<String,String> result=new java.util.TreeMap<>();metrics.forEach((k,v)->result.put(k,"count="+v.count.get()+", avgMs="+String.format(java.util.Locale.ROOT,"%.2f",v.averageMillis())));return result;}
    public final class Timer implements AutoCloseable{private final String name;private final long start;private Timer(String name,long start){this.name=name;this.start=start;}@Override public void close(){if(name!=null){long elapsed=System.nanoTime()-start;Metric m=metrics.computeIfAbsent(name,k->new Metric());m.count.incrementAndGet();m.nanos.addAndGet(elapsed);}}}
    private static final class Metric{private final AtomicLong count=new AtomicLong();private final AtomicLong nanos=new AtomicLong();private double averageMillis(){long c=count.get();return c==0?0:(nanos.get()/1_000_000.0)/c;}}
}
