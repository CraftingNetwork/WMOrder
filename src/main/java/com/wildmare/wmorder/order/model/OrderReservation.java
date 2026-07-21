package com.wildmare.wmorder.order.model;

import java.math.BigDecimal;
import java.util.UUID;

import com.wildmare.wmorder.order.model.OrderStatus;

public record OrderReservation(UUID transactionId, UUID orderId, UUID buyerUuid, UUID sellerUuid,
                               byte[] itemBlob, String itemFingerprint, String itemDisplayName,
                               long quantity, BigDecimal gross, BigDecimal fee, BigDecimal net,
                               OrderStatus previousStatus, long previousVersion, long newVersion, boolean filled) {
    public OrderReservation {
        itemBlob = itemBlob.clone();
    }

    @Override public byte[] itemBlob() { return itemBlob.clone(); }
}
