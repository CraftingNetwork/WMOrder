package com.wildmare.wmorder.order.transaction;

import java.util.UUID;

public final class IdempotencyKeys {
    private IdempotencyKeys(){}
    public static String creation(UUID player,UUID session){return "create:"+player+":"+session;}
    public static String fulfillment(UUID player,UUID order,UUID session){return "fulfill:"+player+":"+order+":"+session;}
}
