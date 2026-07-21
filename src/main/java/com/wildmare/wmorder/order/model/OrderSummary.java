package com.wildmare.wmorder.order.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

public record OrderSummary(
        UUID id,
        UUID buyerUuid,
        String buyerName,
        String itemFingerprint,
        String itemMaterial,
        String itemDisplayName,
        byte[] itemBlob,
        long remainingQuantity,
        long fulfilledQuantity,
        BigDecimal pricePerItem,
        BigDecimal remainingValue,
        Instant createdAt,
        Instant expiresAt,
        OrderStatus status,
        String category,
        long version
) {
    public OrderSummary {
        itemBlob = Arrays.copyOf(itemBlob, itemBlob.length);
    }

    @Override
    public byte[] itemBlob() {
        return Arrays.copyOf(itemBlob, itemBlob.length);
    }
}
