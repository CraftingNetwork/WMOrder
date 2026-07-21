package com.wildmare.wmorder.order.transaction;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public final class OrderGuard {
    private final ConcurrentHashMap<UUID,Entry> guards=new ConcurrentHashMap<>();
    public Token tryAcquire(UUID orderId){
        Entry entry=guards.compute(orderId,(id,current)->{Entry value=current==null?new Entry():current;value.references.incrementAndGet();return value;});
        if(!entry.semaphore.tryAcquire()){releaseReference(orderId,entry);return null;}
        return new Token(orderId,entry);
    }
    private void releaseReference(UUID id,Entry entry){if(entry.references.decrementAndGet()==0&&entry.semaphore.availablePermits()==1)guards.remove(id,entry);}
    public final class Token implements AutoCloseable{private final UUID id;private final Entry entry;private boolean closed;private Token(UUID id,Entry entry){this.id=id;this.entry=entry;}@Override public void close(){if(!closed){closed=true;entry.semaphore.release();releaseReference(id,entry);}}}
    private static final class Entry{private final Semaphore semaphore=new Semaphore(1);private final AtomicInteger references=new AtomicInteger();}
}
